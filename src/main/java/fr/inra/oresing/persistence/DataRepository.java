package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.DownloadDatasetQuery;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import java.sql.PreparedStatement;
import java.util.*;
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
        String query = new DownloadDatasetQueryBuilder(downloadDatasetQuery).buildQuery();
        List result = getNamedParameterJdbcTemplate().query(query, downloadDatasetQuery.getParamSource(), getJsonRowMapper());
        return (List<DataRow>) result;
    }

    public int removeByFileId(UUID fileId) {
        String query = "DELETE FROM " + getTable().getSqlIdentifier() +
                "\n  WHERE binaryfile = :binaryFile";
        final int binaryFile = getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("binaryFile", fileId));
        return binaryFile;
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
                , "  select id dataId,((jsonb_each_text( (jsonb_each(refsLinkedTo)).value)).value)::uuid referencesBy"
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

    public class DownloadDatasetQueryBuilder {
        DownloadDatasetQuery downloadDatasetQuery;
        Application application;

        SqlTable table;

        public DownloadDatasetQueryBuilder(DownloadDatasetQuery downloadDatasetQuery) {
            this.downloadDatasetQuery = downloadDatasetQuery;
        }

        String addOrderBy(String query) {
            Set<DownloadDatasetQuery.VariableComponentOrderBy> variableComponentKeySet = new LinkedHashSet<>();
            String orderBy = Optional.ofNullable(downloadDatasetQuery.getVariableComponentOrderBy())
                    .filter(vck -> !CollectionUtils.isEmpty(vck))
                    .orElseGet(() -> {

                                final Configuration.AuthorizationDescription authorization = downloadDatasetQuery.getApplication().getConfiguration().getDataTypes().get(downloadDatasetQuery.getDataType()).getAuthorization();
                                if (authorization != null && authorization.getTimeScope() != null) {
                                    variableComponentKeySet.add(
                                            new DownloadDatasetQuery.VariableComponentOrderBy(
                                                    authorization.getTimeScope(),
                                                    Order.ASC)
                                    );
                                }
                                if (authorization != null) {
                                    authorization.getAuthorizationScopes().values()
                                            .stream()
                                            .map(Configuration.AuthorizationScopeDescription::getVariableComponentKey)
                                            .forEach(vck -> variableComponentKeySet.add(
                                                    new DownloadDatasetQuery.VariableComponentOrderBy(vck, Order.ASC)
                                            ));
                                }
                                return variableComponentKeySet;
                            }
                    ).stream()
                    .map(vck -> {
                        String format;
                        if ("numeric".equals(vck.type)) {
                            format = "(nullif(datavalues#>>'{\"%s\",\"%s\"}', ''))::numeric";
                        } else {
                            format = "datavalues #>'{\"%s\",\"%s\"}'";
                        }

                        return String.format(
                                format,
                                StringEscapeUtils.escapeSql(vck.getVariable()),
                                StringEscapeUtils.escapeSql(vck.getComponent()),
                                vck.getOrder());
                    })
                    .filter(sorted -> !Strings.isNullOrEmpty(sorted))
                    .collect(Collectors.joining(",  "));
            return Strings.isNullOrEmpty(orderBy) ? query : String.format("%s \nORDER by %s", query, orderBy);
        }

        String filterBy(String query) {
            Set<DownloadDatasetQuery.VariableComponentFilters> variableComponentKeySet = new LinkedHashSet<>();
            String filter = Optional.ofNullable(downloadDatasetQuery.getVariableComponentFilters())
                    .filter(vck -> !CollectionUtils.isEmpty(vck))
                    .orElseGet(LinkedHashSet::new)
                    .stream()
                    .map(vck -> getFormat(vck))
                    .filter(f -> !Strings.isNullOrEmpty(f))
                    .collect(Collectors.joining(" AND "));
            return Strings.isNullOrEmpty(filter) ? query : String.format("%s \nWHERE %s", query, filter);
        }

        private String getFormat(DownloadDatasetQuery.VariableComponentFilters vck) {
            boolean isRegExp = vck.isRegExp != null && vck.isRegExp;
            List<String> filters = new LinkedList<>();
            if (!Strings.isNullOrEmpty(vck.filter)) {
                filters.add(String.format(
                                "datavalues #> '{\"%s\",\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                                StringEscapeUtils.escapeSql(vck.getVariable()),
                                StringEscapeUtils.escapeSql(vck.getComponent()),
                                /*String.format(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */downloadDatasetQuery.addArgumentAndReturnSubstitution(vck.getFilter())//)
                        )
                );

            } else if (vck.intervalValues != null && List.of("date", "time", "datetime").contains(vck.type)) {
                if (!Strings.isNullOrEmpty(vck.intervalValues.from) || !Strings.isNullOrEmpty(vck.intervalValues.to)) {
                    DateLineChecker dateLineChecker = new DateLineChecker(
                            vck.variableComponentKey,
                            vck.format, null, null);
                    filters.add(
                            String.format(
                                    "datavalues #> '{\"%1$s\",\"%2$s\"}'@@ ('$ >= \"date:'||%3$s||'\" && $ <= \"date:'||%4$s||'Z\"')::jsonpath",
                                    StringEscapeUtils.escapeSql(vck.getVariable()),
                                    StringEscapeUtils.escapeSql(vck.getComponent()),
                                    downloadDatasetQuery.addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(vck.intervalValues.from) ? "0" : vck.intervalValues.from),
                                    downloadDatasetQuery.addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(vck.intervalValues.to) ? "9" : vck.intervalValues.to)
                            )
                    );
                }
            } else if (vck.intervalValues != null && "numeric".equals(vck.type)) {
                if (!Strings.isNullOrEmpty(vck.intervalValues.from) || !Strings.isNullOrEmpty(vck.intervalValues.to)) {
                    //datavalues #> '{"t","value"}'@@ '$. double() >= 1 && $. double() <= 2'
                    List<String> filter = new LinkedList<>();
                    if (!Strings.isNullOrEmpty(vck.intervalValues.from)) {
                        filter.add(String.format(
                                        "$. double() >= '||%s||'",
                                        downloadDatasetQuery.addArgumentAndReturnSubstitution(vck.intervalValues.from)
                                )
                        );
                    }
                    if (!Strings.isNullOrEmpty(vck.intervalValues.to)) {
                        filter.add(String.format(
                                        "$. double() <= '||%s||'",
                                        downloadDatasetQuery.addArgumentAndReturnSubstitution(vck.intervalValues.to)
                                )
                        );
                    }
                    filters.add(
                            String.format("datavalues #> '{\"%s\",\"%s\"}'@@ ('%s')::jsonpath",
                                    StringEscapeUtils.escapeSql(vck.getVariable()),
                                    StringEscapeUtils.escapeSql(vck.getComponent()),
                                    filter.stream().collect(Collectors.joining(" && "))
                            )
                    );
                }
            }
            return filters.stream()
                    .filter(filter -> !Strings.isNullOrEmpty(filter))
                    .collect(Collectors.joining(" AND "));
        }

        public String buildQuery() {
            String toMergeDataGroupsQuery = getSqlToMergeData(downloadDatasetQuery);
            String query = "WITH my_data AS (\n" + toMergeDataGroupsQuery + "\n)" +
                    "\n SELECT '" + DataRow.class.getName() + "' AS \"@class\",  " +
                    "\njsonb_build_object(" +
                    "\n\t'rowNumber', row_number() over (), " +
                    "\n\t'totalRows', count(*) over (), " +
                    "\n\t'rowId', rowId, " +
                    "\n\t'values', dataValues, " +
                    "\n\t'refsLinkedTo', refsLinkedTo" +
                    "\n) AS json"
                    + " \nFROM my_data ";
            query = filterBy(query);
            query = addOrderBy(query);
            if (downloadDatasetQuery.getOffset()  != null && downloadDatasetQuery.getLimit()  >= 0) {
                query = String.format("%s \ndownloadDatasetQuery.getOffset()  %d ROWS", query, downloadDatasetQuery.getOffset() );
            }
            if (downloadDatasetQuery.getLimit()  != null && downloadDatasetQuery.getLimit()  >= 0) {
                query = String.format("%s \nFETCH FIRST %d ROW ONLY", query, downloadDatasetQuery.getLimit() );
            }
            return query;
        }
    }

    public static enum Order {
        ASC, DESC
    }
}