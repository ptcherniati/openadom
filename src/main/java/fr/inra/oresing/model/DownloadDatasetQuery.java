package fr.inra.oresing.model;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
/**
 *
 */
public class DownloadDatasetQuery {

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

    Set<AuthorizationDescription> authorizationDescriptions;

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

    public DownloadDatasetQuery(String applicationNameOrId, Application application, Long offset, String dataType, @Nullable Set<VariableComponentKey> variableComponentSelects, @Nullable Set<VariableComponentFilters> variableComponentFilters, @Nullable Set<VariableComponentOrderBy> variableComponentOrderBy, Set<AuthorizationDescription> authorizationDescriptions, Long limit) {
        this.applicationNameOrId = applicationNameOrId;
        this.dataType = dataType;
        this.offset = offset;
        this.limit = limit;
        this.variableComponentSelects = variableComponentSelects;
        this.variableComponentFilters = variableComponentFilters;
        this.variableComponentOrderBy = variableComponentOrderBy;
        this.authorizationDescriptions = authorizationDescriptions;
        this.application = application;

    }

    public static final DownloadDatasetQuery buildDownloadDatasetQuery(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType, Application application) {
        return downloadDatasetQuery == null ?
                new DownloadDatasetQuery(
                        nameOrId,
                        application, null, dataType,
                        null, null, null, null, null
                ) :
                new DownloadDatasetQuery(
                        nameOrId == null ? downloadDatasetQuery.applicationNameOrId : nameOrId,
                        application, downloadDatasetQuery.offset, dataType == null ? downloadDatasetQuery.dataType : dataType,
                        downloadDatasetQuery.variableComponentSelects, downloadDatasetQuery.variableComponentFilters, downloadDatasetQuery.variableComponentOrderBy, downloadDatasetQuery.authorizationDescriptions, downloadDatasetQuery.limit
                );
    }

    @Getter
    @Setter

    public static class VariableComponentOrderBy {
        public VariableComponentKey variableComponentKey;
        public DataRepository.Order order;
        public String type;
        public String format;
        public String key;

        public VariableComponentOrderBy() {
        }

        public VariableComponentOrderBy(VariableComponentKey variableComponentKey, DataRepository.Order order) {
            this.variableComponentKey = variableComponentKey;
            this.order = order;
        }

        public String getId() {
            return variableComponentKey == null ? null : variableComponentKey.getId();
        }

        public String getVariable() {
            return variableComponentKey == null ? null : variableComponentKey.getVariable();
        }

        public String getComponent() {
            return variableComponentKey == null ? null : variableComponentKey.getComponent();
        }

        public String getOrder() {
            return order != null ? order.name() : "ASC";
        }
    }


    @Getter
    @Setter
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

        public String getId() {
            return variableComponentKey == null ? null : variableComponentKey.getId();
        }

        public String getVariable() {
            return variableComponentKey == null ? null : variableComponentKey.getVariable();
        }

        public String getComponent() {
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


    @Getter
    @Setter
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

    @Getter
    @Setter
    /**
     *
     */
    public static class AuthorizationDescription {
        private IntervalValues timeScope;
        private List<Map<String, Ltree>> requiredAuthorizations = new LinkedList<>();

        public List<Authorization> toAuthorization(Application application, SqlSchemaForApplication schema) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String sqlStart = schema.getSqlIdentifier() + ".isauthorized(data.authorization, array[";
            String sqlEnd = "]::" + schema.getSqlIdentifier() + ".authorization[])";
            LocalDateTimeRange localDateTimeRange = timeScope == null ? LocalDateTimeRange.always() : LocalDateTimeRange.getTimeScope(
                    (timeScope.from == null ? null : LocalDate.parse(timeScope.from, dateFormatter)),
                    (timeScope.to == null ? null : LocalDate.parse(timeScope.to, dateFormatter)));
            if(requiredAuthorizations.isEmpty()){
                requiredAuthorizations.add(new HashMap<>());
            }
            return requiredAuthorizations.stream()
                    .map(ltreemap -> new Authorization(
                            List.of(),
                            ltreemap,
                            localDateTimeRange
                    ))
                    .collect(Collectors.toList());
        }
    }
}