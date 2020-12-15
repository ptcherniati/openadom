package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.ReferenceValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ApplicationRepository implements InitializingBean {

    private static final String TEMPLATE_SELECT_ALL = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
    private static final String TEMPLATE_SELECT_BY_ID = TEMPLATE_SELECT_ALL + " WHERE id=:id";

    @Autowired
    private JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private Map<Class, String> sqlUpsert;

    private final Application application;

    private final SqlSchemaForApplication schema;

    public ApplicationRepository(Application application) {
        this.application = application;
        schema = SqlSchema.forApplication(application);
        sqlUpsert = Map.of(
                BinaryFile.class, "INSERT INTO " + schema.binaryFile().getSqlIdentifier() + "(id, application, name, size, data) SELECT id, application, name, size, data FROM json_populate_record(NULL::" + schema.binaryFile().getSqlIdentifier() + ", :json::json) "
                        + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, name=EXCLUDED.name, size=EXCLUDED.size, data=EXCLUDED.data"
                        + " RETURNING id",
                ReferenceValue.class, "INSERT INTO " + schema.referenceValue().getSqlIdentifier() + "(id, application, referenceType, refValues, binaryFile) SELECT id, application, referenceType, refValues, binaryFile FROM json_populate_record(NULL::" + schema.referenceValue().getSqlIdentifier() + ", :json::json) "
                        + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, referenceType=EXCLUDED.referenceType, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile"
                        + " RETURNING id",
                Data.class, "INSERT INTO " + schema.data().getSqlIdentifier() + "(id, application, dataType, timeScope, refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, timeScope, refsLinkedTo, dataValues, binaryFile FROM json_populate_record(NULL::" + schema.data().getSqlIdentifier() + ", :json::json) "
                        + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, timeScope=EXCLUDED.timeScope, refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
                        + " RETURNING id"
        );
    }

    @Override
    public void afterPropertiesSet() {
        // pour force la recuperation petit a petit et pas tout en meme temps (probleme memoire)
        namedParameterJdbcTemplate.getJdbcTemplate().setFetchSize(1000);
    }

    public UUID store(OreSiEntity e) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        String query = Objects.requireNonNull(sqlUpsert.get(e.getClass()));
        String json = jsonRowMapper.toJson(e);
        UUID result = namedParameterJdbcTemplate.queryForObject(
                query, new MapSqlParameterSource("json", json), UUID.class);
        return result;
    }

    /**
     * Supprime un objet dans la base
     * @param table la classe de l'objet a supprimer
     * @param id l'identifiant de l'objet a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
     * @return vrai si un objet a été supprimé
     */
    private boolean delete(SqlTable table, UUID id) {
        String query = String.format("DELETE FROM %s WHERE id=:id", table.getSqlIdentifier());
        int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", id));
        return count > 0;
    }

    public boolean deleteBinaryFile(UUID id) {
        return delete(schema.binaryFile(), id);
    }

    public boolean deleteReferenceValue(UUID id) {
        return delete(schema.referenceValue(), id);
    }

    public boolean deleteData(UUID id) {
        return delete(schema.data(), id);
    }

    public <E extends OreSiEntity> List<E> findAll(Class<E> entityType) {
        String query = String.format(TEMPLATE_SELECT_ALL, entityType.getName(), entityType.getSimpleName());
        List<OreSiEntity> result = namedParameterJdbcTemplate.query(query, jsonRowMapper);
        return (List<E>) result;
    }

    public <E extends OreSiEntity> Optional<E> findById(Class<E> entityType, UUID id) {
        String query = String.format(TEMPLATE_SELECT_BY_ID, entityType.getName(), entityType.getSimpleName());
        Optional<OreSiEntity> result = namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), jsonRowMapper).stream().findFirst();
        return (Optional<E>)result;
    }

    /**
     *
     * @param applicationId l'id de l'application
     * @param refType le type du referenciel
     * @param params les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findReference(UUID applicationId, String refType, MultiValueMap<String, String> params) {
        String query = "SELECT DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                + schema.referenceValue().getSqlIdentifier() + " t, jsonb_each_text(t.refvalues) kv WHERE application=:applicationId::uuid AND referenceType=:refType";
        MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", applicationId)
                .addValue("refType", refType);

        AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
            String k = e.getKey();
            if (StringUtils.equalsAnyIgnoreCase("any", k)) {
                return e.getValue().stream().map(v -> {
                    String arg = ":arg" + i.getAndIncrement();
                    paramSource.addValue(arg, v);
                    return "kv.value=" + arg;
                });
            } else {
                return e.getValue().stream().map(v -> "t.refvalues @> '{\"" + k + "\":\"" + v + "\"}'::jsonb");
            }
        }).collect(Collectors.joining(" OR "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }

        List result = namedParameterJdbcTemplate.query(query + cond, paramSource, jsonRowMapper);
        return (List<ReferenceValue>) result;
    }

    public List<String> findReferenceValue(UUID applicationId, String refType, String column) {
        String sqlPattern = " SELECT refValues->>'%s' "
                          + " FROM " + schema.referenceValue().getSqlIdentifier() + " t"
                          + " WHERE application=:applicationId::uuid AND referenceType=:refType";
        String query = String.format(sqlPattern, column);
        List<String> result = namedParameterJdbcTemplate.queryForList(query,  new MapSqlParameterSource("applicationId", applicationId).addValue("refType", refType), String.class);
        return result;
    }

    public List<Data> findData(UUID applicationId, String dataType, List<UUID> ... nuppletRefs) {
        MapSqlParameterSource args =
                new MapSqlParameterSource("applicationId", applicationId)
                        .addValue("dataType", dataType);

        List<String> condition = new LinkedList<>();
        for (List<UUID> nuppletRef : nuppletRefs) {
            int size = condition.size();
            condition.add(String.format("refslinkedto && ARRAY(select json_array_elements_text(:json%s::json))::uuid[]", size));
            args.addValue("json" + size, jsonRowMapper.toJson(nuppletRef));

        }
        String query = "SELECT '" + Data.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                + schema.data().getSqlIdentifier() + " t WHERE application=:applicationId::uuid AND dataType=:dataType";
        if (condition.size() > 0) {
            query += " AND " + String.join(" AND ", condition);
        }
        List result = namedParameterJdbcTemplate.query(query,  args, jsonRowMapper);
        return (List<Data>) result;
    }

}
