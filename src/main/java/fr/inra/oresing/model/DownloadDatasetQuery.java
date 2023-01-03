package fr.inra.oresing.model;

import fr.inra.oresing.persistence.DataRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
}