package fr.inra.oresing.model.rightsrequest;

import fr.inra.oresing.model.Authorization;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.*;

@Getter
@Setter
@ToString(callSuper = true)
public class RightsRequestInfos {
    @ApiModelProperty(notes = "A list of UUIDS of rightsRequest to find", required = false)
    Set<UUID> uuids = new LinkedHashSet<>();
    @ApiModelProperty(notes = "A list of authorizationss of rightsRequest to find", required = false)
    Set<Authorization> authorizations = new LinkedHashSet<>();
    @ApiModelProperty(notes = "The locale for result default= fr", required = false, example = "FR")
    String locale = Locale.FRANCE.toString();
    @ApiModelProperty(notes = "The first file to return ", required = false, example = "10")
    Long offset = 0L;
    @ApiModelProperty(notes = "The number of files to return ", required = false)
    Long limit; // default "ALL"
    @ApiModelProperty(notes = "A list of fielter  on fields  ", required = false)
    Set<FieldFilters> fieldFilters = new LinkedHashSet<>();

    public enum Order {
        ASC, DESC
    }

    @Getter
    @Setter
    public static class FieldFilters {
        @ApiModelProperty(notes = "The field name to filter  ", required = true)
        public String field;
        @ApiModelProperty(notes = "The value or regexp for filter  ", required = false)
        public String filter;
        @ApiModelProperty(notes = "The type of the value  ", required = false, example = "date, time, datetime, numeric")
        public String type;
        @ApiModelProperty(notes = "The format for date time or datetime type  ", required = false, example = "dd/MM/yyyy")
        public String format;
        @ApiModelProperty(notes = "The interval of values for date, time, datetime or numeric type  ", required = false)
        public IntervalValues intervalValues;
        @ApiModelProperty(notes = "true for regexp filter", required = false)
        public Boolean isRegExp = false;

        public FieldFilters() {
        }

        public FieldFilters(String field, String filter, String type, String format, IntervalValues intervalValues, Boolean isRegExp) {
            this.field = field;
            this.filter = filter;
            this.type = type;
            this.format = format;
            this.intervalValues = intervalValues;
            this.isRegExp = isRegExp;
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