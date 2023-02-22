package fr.inra.oresing.rest;

import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.DataRow;
import lombok.Getter;
import lombok.Setter;
import org.assertj.core.util.Strings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
@Setter
public class DownloadDatasetQuery {
    private AtomicInteger i = new AtomicInteger();
    private MapSqlParameterSource paramSource = new MapSqlParameterSource();

    Application application;
    String applicationNameOrId;
    String dataType;
    String reference;
    String locale;
    Long offset;
    Long limit;
    @Nullable
    Set<VariableComponentKey> variableComponentSelects;
    @Nullable
    Set<VariableComponentFilters> variableComponentFilters;
    @Nullable
    Set<VariableComponentOrderBy> variableComponentOrderBy;

    public DownloadDatasetQuery() {
    }

    public String addArgumentAndReturnSubstitution(Object value) {
        int i = this.i.incrementAndGet();
        String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    public DownloadDatasetQuery(Long offset, Long limit, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentFilters> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy) {
        this.offset = offset;
        this.limit = limit;
        this.variableComponentSelects = variableComponentSelects;
        this.variableComponentFilters = variableComponentFilters;
        this.variableComponentOrderBy = variableComponentOrderBy;
        application = null;
        applicationNameOrId = null;
        dataType = null;
        this.paramSource = new MapSqlParameterSource("applicationId", getApplication().getId());
        // .addValue("refType", refType);
    }

    public DownloadDatasetQuery(String applicationNameOrId, String dataType, Long offset, Long limit, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentFilters> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy, Application application) {
        this.applicationNameOrId = applicationNameOrId;
        this.dataType = dataType;
        this.offset = offset;
        this.limit = limit;
        this.variableComponentSelects = variableComponentSelects;
        this.variableComponentFilters = variableComponentFilters;
        this.variableComponentOrderBy = variableComponentOrderBy;
        this.application = application;

    }

    public List<String> getHiddenVariables() {
        return application.getConfiguration().getDataTypes().get(getDataType()).getData().entrySet().stream()
                .filter(entry -> entry.getValue().isHidden())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<VariableComponentKey> getHiddenComponents() {
        return application.getConfiguration().getDataTypes().get(getDataType()).getData().entrySet().stream()
                .map(entryVariable -> {
                    String variableName = entryVariable.getKey();
                    LinkedHashMap<String, Configuration.VariableComponentDescription> components = new LinkedHashMap<>();
                    components.putAll(entryVariable.getValue().getComponents());
                    components.putAll(entryVariable.getValue().getComputedComponents());
                    return components.entrySet().stream()
                            .filter(entry -> entry.getValue()!=null && entry.getValue().isHidden())
                            .map(Map.Entry::getKey)
                            .map(componentName -> new VariableComponentKey(variableName, componentName))
                            .collect(Collectors.toList());
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public static final DownloadDatasetQuery buildDownloadDatasetQuery(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType, Application application) {
        return downloadDatasetQuery == null ?
                new DownloadDatasetQuery(
                        nameOrId,
                        dataType,
                        null,
                        null,
                        null,
                        null,
                        null,
                        application) :
                new DownloadDatasetQuery(
                        nameOrId == null ? downloadDatasetQuery.applicationNameOrId : nameOrId,
                        dataType == null ? downloadDatasetQuery.dataType : dataType,
                        downloadDatasetQuery.offset,
                        downloadDatasetQuery.limit,
                        downloadDatasetQuery.variableComponentSelects,
                        downloadDatasetQuery.variableComponentFilters,
                        downloadDatasetQuery.variableComponentOrderBy,
                        application);
    }

    String addOrderBy(String query) {
        Set<VariableComponentOrderBy> variableComponentKeySet = new LinkedHashSet<>();
        String orderBy = Optional.ofNullable(variableComponentOrderBy)
                .filter(vck -> !CollectionUtils.isEmpty(vck))
                .orElseGet(() -> {

                            final Configuration.AuthorizationDescription authorization = getApplication().getConfiguration().getDataTypes().get(getDataType()).getAuthorization();
                            if (authorization != null && authorization.getTimeScope() != null) {
                                variableComponentKeySet.add(
                                        new VariableComponentOrderBy(
                                                authorization.getTimeScope(),
                                                Order.ASC)
                                );
                            }
                            if (authorization != null) {
                                authorization.getAuthorizationScopes().values()
                                        .stream()
                                        .map(Configuration.AuthorizationScopeDescription::getVariableComponentKey)
                                        .forEach(vck -> variableComponentKeySet.add(
                                                new VariableComponentOrderBy(vck, Order.ASC)
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
        Set<VariableComponentFilters> variableComponentKeySet = new LinkedHashSet<>();
        String filter = Optional.ofNullable(variableComponentFilters)
                .filter(vck -> !CollectionUtils.isEmpty(vck))
                .orElseGet(LinkedHashSet::new)
                .stream()
                .map(vck -> getFormat(vck))
                .filter(f -> !Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND "));
        return Strings.isNullOrEmpty(filter) ? query : String.format("%s \nWHERE %s", query, filter);
    }

    private String getFormat(VariableComponentFilters vck) {
        boolean isRegExp = vck.isRegExp != null && vck.isRegExp;
        List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(vck.filter)) {
            filters.add(String.format(
                            "datavalues #> '{\"%s\",\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                            StringEscapeUtils.escapeSql(vck.getVariable()),
                            StringEscapeUtils.escapeSql(vck.getComponent()),
                            /*String.format(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */addArgumentAndReturnSubstitution(vck.getFilter())//)
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
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(vck.intervalValues.from) ? "0" : vck.intervalValues.from),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(vck.intervalValues.to) ? "9" : vck.intervalValues.to)
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
                                    addArgumentAndReturnSubstitution(vck.intervalValues.from)
                            )
                    );
                }
                if (!Strings.isNullOrEmpty(vck.intervalValues.to)) {
                    filter.add(String.format(
                                    "$. double() <= '||%s||'",
                                    addArgumentAndReturnSubstitution(vck.intervalValues.to)
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
    private String buildDeleteJsonPathSql(List<String> pathes){
        return pathes.stream().collect(Collectors.joining(",", "#- '{", "}'"));
    }

    public String buildQuery(String toMergeDataGroupsQuery) {
        String filterHiddenVariable = filterHiddenVariables();
        String filterHiddenComponents= filterHiddenComponents();
        String query = "WITH my_data AS (\n" + toMergeDataGroupsQuery + "\n)" +
                "\n SELECT '" + DataRow.class.getName() + "' AS \"@class\",  " +
                "\njsonb_build_object(" +
                "\n\t'rowNumber', row_number() over (), " +
                "\n\t'totalRows', count(*) over (), " +
                "\n\t'rowId', rowId, " +
                "\n\t'values', dataValues "+filterHiddenVariable+" "+filterHiddenComponents+", " +
                "\n\t'refsLinkedTo', refsLinkedTo "+filterHiddenVariable+" "+filterHiddenComponents+" " +
                "\n) AS json"
                + " \nFROM my_data ";
        query = filterBy(query);
        query = addOrderBy(query);
        if (offset != null && limit >= 0) {
            query = String.format("%s \nOFFSET %d ROWS", query, offset);
        }
        if (limit != null && limit >= 0) {
            query = String.format("%s \nFETCH FIRST %d ROW ONLY", query, limit);
        }
        return query;
    }

    private String filterHiddenVariables() {
        return getHiddenVariables().stream()
                .map(List::of)
                .map(this::buildDeleteJsonPathSql)
                .collect(Collectors.joining(" "));
    }

    private String filterHiddenComponents() {
        return getHiddenComponents().stream()
                .map(vc -> List.of(vc.getVariable(), vc.getComponent()))
                .map(this::buildDeleteJsonPathSql)
                .collect(Collectors.joining(" "));
    }

    public enum Order {
        ASC, DESC
    }

    public static class VariableComponentOrderBy {
        public VariableComponentKey variableComponentKey;
        public Order order;
        public String type;
        public String format;
        public String key;

        public VariableComponentOrderBy() {
        }

        public VariableComponentOrderBy(VariableComponentKey variableComponentKey, Order order) {
            this.variableComponentKey = variableComponentKey;
            this.order = order;
        }

        String getId() {
            return variableComponentKey == null ? null : variableComponentKey.getId();
        }

        String getVariable() {
            return variableComponentKey == null ? null : variableComponentKey.getVariable();
        }

        String getComponent() {
            return variableComponentKey == null ? null : variableComponentKey.getComponent();
        }

        public String getOrder() {
            return order != null ? order.name() : "ASC";
        }
    }

    public static class VariableComponentFilters {
        public VariableComponentKey variableComponentKey;
        public String filter;
        public String type;
        public String format;
        public IntervalValues intervalValues;
        public Boolean isRegExp = false;

        public VariableComponentFilters() {
        }

        public VariableComponentFilters(VariableComponentKey variableComponentKey, String filter, String type, String format, IntervalValues intervalValues, Boolean isRegExp) {
            this.variableComponentKey = variableComponentKey;
            this.filter = filter;
            this.type = type;
            this.format = format;
            this.intervalValues = intervalValues;
            this.isRegExp = isRegExp;
        }

        String getId() {
            return variableComponentKey == null ? null : variableComponentKey.getId();
        }

        String getVariable() {
            return variableComponentKey == null ? null : variableComponentKey.getVariable();
        }

        String getComponent() {
            return variableComponentKey == null ? null : variableComponentKey.getComponent();
        }

        public String getFilter() {
            return filter != null ? filter : null;
        }

        public Boolean isNumeric() {
            return "numeric".equals(type);
        }

        public Boolean isdDate() {
            return "date".equals(type);
        }
    }

    public static class IntervalValues {
        public String from;
        public String to;

        public IntervalValues(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public IntervalValues() {
        }
    }
}