package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.UnmodifiableIterator;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.ReferenceValue;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ApplicationRepository implements InitializingBean {

    @Autowired
    private JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Application application;

    private final SqlSchemaForApplication schema;

    public ApplicationRepository(Application application) {
        this.application = application;
        schema = SqlSchema.forApplication(application);
    }

    @Override
    public void afterPropertiesSet() {
        // pour force la recuperation petit a petit et pas tout en meme temps (probleme memoire)
        namedParameterJdbcTemplate.getJdbcTemplate().setFetchSize(1000);
    }

    public DataDao data() {
        return new DataDao();
    }

    public ReferenceValueDao referenceValue() {
        return new ReferenceValueDao();
    }

    public BinaryFileDao binaryFile() {
        return new BinaryFileDao();
    }

    public abstract class SortOfDao<T extends OreSiEntity> {

        private UnmodifiableIterator<List<T>> partition(Stream<T> stream) {
            // 7min19 pour 10
            // 6min07 pour 30
            // 6min15 pour 40
            // 5min46 pour 50
            // 5min48 pour 100
            // 5min50 pour 500
            // 6min21 pour 1000
            return Iterators.partition(stream.iterator(), 50);
        }

        public void storeAll(Stream<T> stream) {
            String query = getUpsertQuery();
            partition(stream).forEachRemaining(entities -> {
                entities.forEach(e -> {
                    if (e.getId() == null) {
                        e.setId(UUID.randomUUID());
                    }
                });
                String json = jsonRowMapper.toJson(entities);
                List<UUID> result = namedParameterJdbcTemplate.queryForList(
                        query, new MapSqlParameterSource("json", json), UUID.class);
            });
        }

        protected abstract String getUpsertQuery();

        public UUID store(T entity) {
            UUID id = entity.getId();
            storeAll(Stream.of(entity));
            return id;
        }

        /**
         * Supprime un objet dans la base
         *
         * @param id l'identifiant de l'objet a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
         * @return vrai si un objet a été supprimé
         */
        public boolean delete(UUID id) {
            SqlTable table = getTable();
            String query = String.format("DELETE FROM %s WHERE id=:id", table.getSqlIdentifier());
            int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", id));
            return count > 0;
        }

        protected abstract SqlTable getTable();

        private JsonRowMapper<T> getJsonRowMapper() {
            return (JsonRowMapper<T>) jsonRowMapper;
        }

        public T findById(UUID id) {
            Preconditions.checkArgument(id != null);
            String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json FROM %s t WHERE id = :id", getEntityClass().getName(), getTable().getSqlIdentifier());
            JsonRowMapper<T> jsonRowMapper = getJsonRowMapper();
            T result = namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), jsonRowMapper).stream().collect(MoreCollectors.onlyElement());
            return result;
        }

        protected abstract Class<T> getEntityClass();
    }

    public class DataDao extends SortOfDao<Data> {

        @Override
        public SqlTable getTable() {
            return schema.data();
        }

        @Override
        protected String getUpsertQuery() {
            return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, dataType, rowId, dataGroup, localizationScope, timeScope, refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, rowId, dataGroup, localizationScope, timeScope, refsLinkedTo, dataValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, rowId=EXCLUDED.rowId, dataGroup=EXCLUDED.rowId, localizationScope=EXCLUDED.localizationScope, timeScope=EXCLUDED.timeScope, refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
                    + " RETURNING id";
        }

        @Override
        protected Class<Data> getEntityClass() {
            return Data.class;
        }

        public List<Map<String, Map<String, String>>> findAllByDataType(String dataType) {
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

        public int migrate(String dataType, String dataGroup, Map<String, Map<String, String>> variablesToAdd, Set<UUID> refsLinkedToToAdd) {
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
    }

    public class ReferenceValueDao extends SortOfDao<ReferenceValue> {

        @Override
        public SqlTable getTable() {
            return schema.referenceValue();
        }

        @Override
        protected String getUpsertQuery() {
            return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, referenceType, compositeKey, refValues, binaryFile) SELECT id, application, referenceType, compositeKey, refValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, referenceType=EXCLUDED.referenceType, compositeKey=EXCLUDED.compositeKey, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile"
                    + " RETURNING id";
        }

        @Override
        protected Class<ReferenceValue> getEntityClass() {
            return ReferenceValue.class;
        }

        public List<ReferenceValue> findAllByReferenceType(String refType) {
            return referenceValue().findAllByReferenceType(refType, new LinkedMultiValueMap<String, String>());
        }

        /**
         *
         * @param refType le type du referenciel
         * @param params les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
         * @return la liste qui satisfont aux criteres
         */
        public List<ReferenceValue> findAllByReferenceType(String refType, MultiValueMap<String, String> params) {
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

        public ImmutableMap<String, UUID> getReferenceIdPerKeys(String referenceType) {
            return findAllByReferenceType(referenceType).stream()
                    .collect(ImmutableMap.toImmutableMap(ReferenceValue::getCompositeKey, ReferenceValue::getId));
        }
    }

    public class BinaryFileDao extends SortOfDao<BinaryFile> {

        @Override
        public SqlTable getTable() {
            return schema.binaryFile();
        }

        @Override
        protected String getUpsertQuery() {
            return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, name, size, data) SELECT id, application, name, size, data FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, name=EXCLUDED.name, size=EXCLUDED.size, data=EXCLUDED.data"
                    + " RETURNING id";
        }

        @Override
        protected Class<BinaryFile> getEntityClass() {
            return BinaryFile.class;
        }
    }
}
