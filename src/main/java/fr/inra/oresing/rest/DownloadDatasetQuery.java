package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.DataRow;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
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
    Set<VariableComponentKey> variableComponentFilters;
    @Nullable
    Set<VariableComponentOrderBy> variableComponentOrderBy;

    public DownloadDatasetQuery() {
    }

    public DownloadDatasetQuery(Long offset, Long limit, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentKey> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy) {
        this.offset = offset;
        this.limit = limit;
        this.variableComponentSelects = variableComponentSelects;
        this.variableComponentFilters = variableComponentFilters;
        this.variableComponentOrderBy = variableComponentOrderBy;
        application = null;
        applicationNameOrId = null;
        dataType = null;
    }

    public DownloadDatasetQuery(String applicationNameOrId, String dataType, Long offset, Long limit, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentKey> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy, Application application) {
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
                .filter(vck->!CollectionUtils.isEmpty(vck))
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
                .map(vck -> String.format("datavalues->'%s'->>'%s' %s", StringEscapeUtils.escapeSql(vck.getVariable()), StringEscapeUtils.escapeSql(vck.getComponent()), vck.getOrder()))
                .collect(Collectors.joining(","));
        return String.format("%s \nORDER by %s", query, orderBy);
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
            return order!=null?order.name():"ASC";
        }
    }
}
