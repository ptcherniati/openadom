package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.rest.DownloadDatasetQuery;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, dataType, rowId, \"authorization\", refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, rowId, \"authorization\", refsLinkedTo, dataValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, rowId=EXCLUDED.rowId, \"authorization\"=EXCLUDED.\"authorization\", refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
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
                "\n  WHERE binaryfile = :binaryFile";
        return getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("binaryFile", fileId));
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

    public int migrate(String dataType, String dataGroup, Map<String, Map<String, String>> variablesToAdd, Map<String, Map<String, UUID>> refsLinkedToToAdd) {
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

    public void updateConstraintForeigData(List<UUID> uuids) {
        String sql = "INSERT INTO " + getTable().getSchema().getSqlIdentifier() + ".Data_Reference(dataId, referencedBy)\n" +
                "with tuple as (\n" +
                "select id dataId,((jsonb_each_text( (jsonb_each(refsLinkedTo)).value)).value)::uuid referencedBy\n" +
                "from " + getTable().getSqlIdentifier() + "\n" +
                ")\n" +
                "select dataId, referencedBy from tuple\n" +
                "where dataId in (:ids) and referencedBy is not null\n" +
                "ON CONFLICT ON CONSTRAINT \"Data_Reference_PK\" DO NOTHING;";
        final String ids = uuids.stream()
                .map(uuid -> String.format("'%s'::uuid", uuid))
                .collect(Collectors.joining(","));
        try {
            List result = getNamedParameterJdbcTemplate().query(sql, ImmutableMap.of("ids", uuids), getJsonRowMapper());
        } catch (DataIntegrityViolationException e) {
            if(e.getCause() instanceof PSQLException && !"02000".equals(((PSQLException)e.getCause()).getSQLState())){
                throw e;
            }
        }
    }
}