package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.rest.DownloadDatasetQuery;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataRepository extends JsonTableInApplicationSchemaRepositoryTemplate<Data> {

    public DataRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().data();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, dataType, rowId, \"authorization\", uniqueness, refsLinkedTo, dataValues, binaryFile) \n" +
                "SELECT id, application, dataType, rowId, \"authorization\", uniqueness,  refsLinkedTo, dataValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (dataType, datagroup, uniqueness) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, rowId=EXCLUDED.rowId, \"authorization\"=EXCLUDED.\"authorization\", refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
                + " RETURNING id";
    }

    @Override
    protected Class<Data> getEntityClass() {
        return Data.class;
    }

    public List<DataRow> findAllByDataType(DownloadDatasetQuery downloadDatasetQuery) {
        String toMergeDataGroupsQuery = getSqlToMergeData(downloadDatasetQuery);
        String query = downloadDatasetQuery.buildQuery(toMergeDataGroupsQuery);
        List result = getNamedParameterJdbcTemplate().query(query, downloadDatasetQuery.getParamSource(), getJsonRowMapper());
        return (List<DataRow>) result;
    }

    public int removeByFileId(UUID fileId) {
        String query = "DELETE FROM " + getTable().getSqlIdentifier() +
                "\n  WHERE binaryfile::text in( :binaryFile)";
        final ImmutableMap<String, List<String>> params = ImmutableMap.of("binaryFile", List.of(fileId.toString()));
        final int unPublishdLines = getNamedParameterJdbcTemplate().update(query, params);
        flush();
        return unPublishdLines;
    }

    public String getSqlToMergeData(DownloadDatasetQuery downloadDatasetQuery) {
        String dataType = downloadDatasetQuery.getDataType();
        Preconditions.checkArgument(getApplication().getDataType().contains(dataType), "pas de type de données " + dataType + " dans l'application " + getApplication());
        String applicationId = getApplication().getId().toString();
        String sql = " SELECT \n\trowId, \n\tjsonb_object_agg(datavalues) AS dataValues, \n\tjsonb_object_agg(refsLinkedTo) AS refsLinkedTo"
                + " \nFROM " + getTable().getSqlIdentifier()
                + " \nWHERE application = '" + applicationId + "'::uuid \n\tAND dataType = '" + dataType + "'"
                + " \nGROUP BY rowId";
        return sql;
    }

    public int migrate(String dataType, String dataGroup, Map<String, Map<String, String>> variablesToAdd, Map<String, Map<String, Set<UUID>>> refsLinkedToToAdd) {
        String setRefsLinkedToClause;
        if (refsLinkedToToAdd.isEmpty()) {
            setRefsLinkedToClause = "";
        } else {
            String refsLinkedToToAddAsJson = getJsonRowMapper().toJson(refsLinkedToToAdd);
            setRefsLinkedToClause = ", refsLinkedTo = refsLinkedTo || '" + refsLinkedToToAddAsJson + "'::jsonb";
        }
        String dataValuesAsJson = getJsonRowMapper().toJson(variablesToAdd);
        String sql = " UPDATE " + getTable().getSqlIdentifier()
                + " SET dataValues = dataValues || '" + dataValuesAsJson + "'::jsonb"
                + setRefsLinkedToClause
                + " WHERE application = :applicationId::uuid AND dataType = :dataType AND dataGroup = :dataGroup";
        MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("dataType", dataType)
                .addValue("dataGroup", dataGroup);
        int count = getNamedParameterJdbcTemplate().update(sql, sqlParams);
        return count;
    }

    public List<Uniqueness> findStoredUniquenessForDatatype(Application application, String dataType) {
        String query = "select 'fr.inra.oresing.persistence.Uniqueness' as \"@class\",uniqueness as json from  " + getTable().getSqlIdentifier()
                + "  WHERE application = :applicationId::uuid AND dataType = :dataType";
        MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("dataType", dataType);
        return getNamedParameterJdbcTemplate().query(query, sqlParams, new JsonRowMapper<Uniqueness>());
    }

    public void updateConstraintForeignData(List<UUID> uuids) {
        String deleteSql = "DELETE FROM " + getTable().getSchema().getSqlIdentifier() + ".Data_Reference WHERE dataId in (:ids)";
        String insertSql = String.join(" "
                , "INSERT INTO " + getTable().getSchema().getSqlIdentifier() + ".Data_Reference(dataId, referencesBy)"
                , "with tuple as ("
                , "  select id dataId, "
                ,"    (jsonb_path_query(refsLinkedTo, '$.*.* ? (@!= null)')#>> '{}') ::uuid referencesBy"
                , "  from " + getTable().getSqlIdentifier() + ""
                , ")"
                , "select dataId, referencesBy from tuple"
                , "where dataId in (:ids) and referencesBy is not null"
                , "ON CONFLICT ON CONSTRAINT \"Data_Reference_PK\" DO NOTHING"
        );
        String sql = String.join(";", deleteSql, insertSql);
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE-1)
                .forEachRemaining(uuidsByBatch -> getNamedParameterJdbcTemplate().execute(sql, ImmutableMap.of("ids", uuidsByBatch), PreparedStatement::execute));
    }
}