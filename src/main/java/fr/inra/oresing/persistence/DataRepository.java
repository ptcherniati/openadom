package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Data;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, dataType, rowId, dataGroup, requiredAuthorizations, timeScope, refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, rowId, dataGroup, requiredAuthorizations, timeScope, refsLinkedTo, dataValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, rowId=EXCLUDED.rowId, dataGroup=EXCLUDED.rowId, requiredAuthorizations=EXCLUDED.requiredAuthorizations, timeScope=EXCLUDED.timeScope, refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
                + " RETURNING id";
    }

    @Override
    protected Class<Data> getEntityClass() {
        return Data.class;
    }

    public List<DataRow> findAllByDataType(String dataType, Long offset, Long limit) {
        String toMergeDataGroupsQuery = getSqlToMergeData(dataType);
        String query = "WITH my_data AS (" + toMergeDataGroupsQuery + ")"
                + " SELECT '" + DataRow.class.getName() + "' AS \"@class\",  " +
                "jsonb_build_object(" +
                "'rowNumber', row_number() over (), " +
                "'totalRows', count(*) over (), " +
                "'rowId', rowId, " +
                "'values', dataValues, " +
                "'refsLinkedTo', refsLinkedTo" +
                ") AS json"
                + " FROM my_data ";
        if (offset != null) {
            query = String.format("%s \nOFFSET %d ROWS", query, offset);
        }
        if (limit != null) {
            query = String.format("%s \nFETCH FIRST %d ROW ONLY", query, limit);
        }
        List result = getNamedParameterJdbcTemplate().query(query, Collections.emptyMap(), getJsonRowMapper());
        return (List<DataRow>) result;
    }

    public String getSqlToMergeData(String dataType) {
        Preconditions.checkArgument(getApplication().getDataType().contains(dataType), "pas de type de données " + dataType + " dans l'application " + getApplication());
        String applicationId = getApplication().getId().toString();
        String sql = " SELECT rowId, jsonb_object_agg(regexp_replace(datavalues::text, 'date:\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}:(.*)', '\\1')::jsonb) AS dataValues, jsonb_object_agg(refsLinkedTo) AS refsLinkedTo"
                + " FROM " + getTable().getSqlIdentifier()
                + " WHERE application = '" + applicationId + "'::uuid AND dataType = '" + dataType + "'"
                + " GROUP BY rowId";
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
}
