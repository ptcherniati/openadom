package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.DataRow;
import lombok.Getter;
import lombok.Setter;
import org.assertj.core.util.Strings;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class DownloadDatasetQuery {
    Application application;
    String applicationNameOrId;
    String dataType;
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

    public DownloadDatasetQuery(Long offset, Long limit, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentFilters> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy) {
        this.offset = offset;
        this.limit = limit;
        this.variableComponentSelects = variableComponentSelects;
        this.variableComponentFilters = variableComponentFilters;
        this.variableComponentOrderBy = variableComponentOrderBy;
        application = null;
        applicationNameOrId = null;
        dataType = null;
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
                            variableComponentKeySet.add(
                                    new VariableComponentOrderBy(
                                            getApplication().getConfiguration().getDataTypes().get(getDataType()).getAuthorization().getTimeScope(),
                                            Order.ASC)
                            );
                            getApplication().getConfiguration().getDataTypes().get(getDataType()).getAuthorization().getAuthorizationScopes().values()
                                    .stream()
                                    .forEach(vck -> variableComponentKeySet.add(
                                            new VariableComponentOrderBy(vck, Order.ASC)
                                    ));
                            return variableComponentKeySet;
                        }
                ).stream()
                .map(vck -> {
                    String format;
                    if("numeric".equals(vck.type)){
                        format = "(nullif(datavalues->'%s'->>'%s', ''))::numeric  %s";
                    }else{
                        format = "datavalues->'%s'->>'%s' %s";
                    }

                    return String.format(format, StringEscapeUtils.escapeSql(vck.getVariable()), StringEscapeUtils.escapeSql(vck.getComponent()), vck.getOrder());
                })
                .filter(sorted ->!Strings.isNullOrEmpty(sorted))
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
                .filter(f->!Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND "));
        return Strings.isNullOrEmpty(filter) ? query : String.format("%s \nWHERE %s", query, filter);
    }

    private String getFormat(VariableComponentFilters vck) {
        List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(vck.filter)) {
            filters.add(String.format(
                    "datavalues->'%s'->>'%s' like %s",
                    StringEscapeUtils.escapeSql(vck.getVariable()),
                    StringEscapeUtils.escapeSql(vck.getComponent()),
                    vck.getFilter())
            );

        } else if (vck.intervalValues != null && List.of("date", "time", "datetime").contains(vck.type)) {
            if (! Strings.isNullOrEmpty(vck.intervalValues.from) || ! Strings.isNullOrEmpty(vck.intervalValues.to)) {
                filters.add(
                        String.format(
                                "to_timestamp(datavalues->'%s'->>'%s', '%s')  BETWEEN '%s'::TIMESTAMP AND '%s'::TIMESTAMP",
                                StringEscapeUtils.escapeSql(vck.getVariable()),
                                StringEscapeUtils.escapeSql(vck.getComponent()),
                                vck.format,
                                Strings.isNullOrEmpty(vck.intervalValues.from) ? "-infinity" :vck.intervalValues.from ,
                                Strings.isNullOrEmpty(vck.intervalValues.to) ? "infinity" : vck.intervalValues.to
                        )
                );
            }
        } else if (vck.intervalValues != null && "numeric".equals(vck.type)) {
            if(!Strings.isNullOrEmpty(vck.intervalValues.from) || !Strings.isNullOrEmpty(vck.intervalValues.to)) {
                if (!Strings.isNullOrEmpty(vck.intervalValues.from)) {
                    filters.add(String.format(
                                    "(nullif(datavalues->'%s'->>'%s', ''))::numeric >= '%s'::numeric",
                                    StringEscapeUtils.escapeSql(vck.getVariable()),
                                    StringEscapeUtils.escapeSql(vck.getComponent()),
                                    vck.intervalValues.from
                            )
                    );
                }
                if (!Strings.isNullOrEmpty(vck.intervalValues.to)) {
                    filters.add(String.format(
                                    "(nullif(datavalues->'%s'->>'%s', ''))::numeric < '%s'::numeric",
                                    StringEscapeUtils.escapeSql(vck.getVariable()),
                                    StringEscapeUtils.escapeSql(vck.getComponent()),
                                    vck.intervalValues.to
                            )
                    );
                }
            }
        }
        return filters.stream()
                .filter(filter ->!Strings.isNullOrEmpty(filter))
                .collect(Collectors.joining(" AND "));
    }

    public String buildQuery(String toMergeDataGroupsQuery) {
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

        public VariableComponentFilters() {
        }

        public VariableComponentFilters(VariableComponentKey variableComponentKey, String filter, String type, String format, IntervalValues intervalValues) {
            this.variableComponentKey = variableComponentKey;
            this.filter = filter;
            this.type = type;
            this.format = format;
            this.intervalValues = intervalValues;
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
            return filter != null ? String.format(" '%%%s%%'", filter) : null;
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
