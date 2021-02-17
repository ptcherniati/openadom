package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.MoreCollectors;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
                Data.class, "INSERT INTO " + schema.data().getSqlIdentifier() + "(id, application, dataType, rowId, dataGroup, localizationScope, timeScope, refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, rowId, dataGroup, localizationScope, timeScope, refsLinkedTo, dataValues, binaryFile FROM json_populate_record(NULL::" + schema.data().getSqlIdentifier() + ", :json::json) "
                        + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, rowId=EXCLUDED.rowId, dataGroup=EXCLUDED.rowId, localizationScope=EXCLUDED.localizationScope, timeScope=EXCLUDED.timeScope, refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
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

    public BinaryFile findBinaryFileById(UUID id) {
        Preconditions.checkArgument(id != null);
        String query = String.format(TEMPLATE_SELECT_BY_ID, BinaryFile.class.getName(), schema.binaryFile().getSqlIdentifier());
        JsonRowMapper<BinaryFile> jsonRowMapper = getJsonRowMapper();
        BinaryFile result = namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), jsonRowMapper).stream().collect(MoreCollectors.onlyElement());
        return result;
    }

    private <T> JsonRowMapper<T> getJsonRowMapper() {
        return (JsonRowMapper<T>) jsonRowMapper;
    }

    public List<ReferenceValue> findReference(String refType) {
        return findReference(refType, new LinkedMultiValueMap<String, String>());
    }

    /**
     *
     * @param refType le type du referenciel
     * @param params les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findReference(String refType, MultiValueMap<String, String> params) {
        String query = "SELECT DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                + schema.referenceValue().getSqlIdentifier() + " t, jsonb_each_text(t.refvalues) kv WHERE application=:applicationId::uuid AND referenceType=:refType";
        MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", application.getId())
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

    public List<String> findReferenceValue(String refType, String column) {
        String sqlPattern = " SELECT refValues->>'%s' "
                          + " FROM " + schema.referenceValue().getSqlIdentifier() + " t"
                          + " WHERE application=:applicationId::uuid AND referenceType=:refType";
        String query = String.format(sqlPattern, column);
        List<String> result = namedParameterJdbcTemplate.queryForList(query,  new MapSqlParameterSource("applicationId", application.getId()).addValue("refType", refType), String.class);
        return result;
    }

    public List<Map<String, Map<String, String>>> findData(String dataType) {
        String toMergeDataGroupsQuery = getSqlToMergeData(dataType);
        String query = "WITH my_data AS (" + toMergeDataGroupsQuery + ")"
                + " SELECT '" + Map.class.getName() + "' AS \"@class\", to_jsonb(dataValues) AS json"
                + " FROM my_data";
        List result = namedParameterJdbcTemplate.query(query, Collections.emptyMap(), jsonRowMapper);
        return (List<Map<String, Map<String, String>>>) result;
    }

    public String getSqlToMergeData(String dataType) {
        Preconditions.checkArgument(application.getDataType().contains(dataType), "pas de type de données " + dataType + " dans l'application " + application);
        String applicationId = application.getId().toString();
        String sql = " SELECT rowId, jsonb_object_agg(dataValues) AS dataValues, aggregate_by_array_concatenation(refsLinkedTo) AS refsLinkedTo"
                   + " FROM " + schema.data().getSqlIdentifier()
                   + " WHERE application = '" + applicationId + "'::uuid AND dataType = '" + dataType + "'"
                   + " GROUP BY rowId";
        return sql;
    }

    public int migrateData(String dataType, String dataGroup, Map<String, Map<String, String>> variablesToAdd, Set<UUID> refsLinkedToToAdd) {
        String setRefsLinkedToClause;
        if (refsLinkedToToAdd.isEmpty()) {
            setRefsLinkedToClause = "";
        } else {
            setRefsLinkedToClause = ", refsLinkedTo = refsLinkedTo || :refsLinkedToToAdd ";
        }
        String json = jsonRowMapper.toJson(variablesToAdd);
        String sql = " UPDATE " + schema.data().getSqlIdentifier()
                   + " SET dataValues = dataValues || '" + json + "'::jsonb"
                   + setRefsLinkedToClause
                   + " WHERE application = :applicationId::uuid AND dataType = :dataType AND dataGroup = :dataGroup"
                   ;
        MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", application.getId())
                .addValue("dataType", dataType)
                .addValue("dataGroup", dataGroup)
                .addValue("refsLinkedToToAdd", refsLinkedToToAdd)
                ;
        int count = namedParameterJdbcTemplate.update(sql, sqlParams);
        return count;
    }

    public ImmutableBiMap<String, UUID> getReferenceIdPerKeys(String referenceType, String keyColumn) {
        Function<ReferenceValue, String> getKeyFn = referenceValue -> referenceValue.getRefValues().get(keyColumn);
        return findReference(referenceType).stream()
                .collect(ImmutableBiMap.toImmutableBiMap(getKeyFn, ReferenceValue::getId));
    }
}
