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
        List result = getNamedParameterJdbcTemplate().query(query, Collections.emptyMap(), getJsonRowMapper());
        return (List<Map<String, Map<String, String>>>) result;
    }

    public String getSqlToMergeData(String dataType) {
        Preconditions.checkArgument(getApplication().getDataType().contains(dataType), "pas de type de données " + dataType + " dans l'application " + getApplication());
        String applicationId = getApplication().getId().toString();
        String sql = " SELECT rowId, jsonb_object_agg(dataValues) AS dataValues, aggregate_by_array_concatenation(refsLinkedTo) AS refsLinkedTo"
                + " FROM " + getTable().getSqlIdentifier()
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
        String json = getJsonRowMapper().toJson(variablesToAdd);
        String sql = " UPDATE " + getTable().getSqlIdentifier()
                + " SET dataValues = dataValues || '" + json + "'::jsonb"
                + setRefsLinkedToClause
                + " WHERE application = :applicationId::uuid AND dataType = :dataType AND dataGroup = :dataGroup";
        MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("dataType", dataType)
                .addValue("dataGroup", dataGroup)
                .addValue("refsLinkedToToAdd", refsLinkedToToAdd);
        int count = getNamedParameterJdbcTemplate().update(sql, sqlParams);
        return count;
    }
}
