package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

@Value
public class DateValidationCheckResult implements ValidationCheckResult {
    ValidationLevel level;
    String message;
    Map<String, Object> messageParams;
    VariableComponentKey variableComponentKey;
    String column;
    TemporalAccessor date;

    public DateValidationCheckResult(ValidationLevel level, String message, Map<String, Object> messageParams, VariableComponentKey variableComponentKey, String column, TemporalAccessor date) {
        this.level = level;
        this.message = message;
        this.messageParams = messageParams;
        this.variableComponentKey = variableComponentKey;
        this.column = column;
        this.date = date;
    }

    public DateValidationCheckResult(ValidationLevel success, Map<String, Object> params) {
        this.messageParams = params;
        this.level = success;
        this.column = (String) Optional.ofNullable(messageParams).map(mp->mp.getOrDefault(DateLineChecker.PARAM_COLUMN, null)).orElse(null);
        this.variableComponentKey = (VariableComponentKey) Optional.ofNullable(messageParams).map(mp->mp.getOrDefault(DateLineChecker.PARAM_VARIABLE_COMPONENT_KEY, null)).orElse(null);
        this.date = (TemporalAccessor) Optional.ofNullable(messageParams).map(mp->mp.getOrDefault(DateLineChecker.PARAM_DATE, null)).orElse(null);
        LocalDateTime localDateTime = LocalDateTime.MIN;
        if(date!=null){
            LocalDate localdate = date.query(TemporalQueries.localDate());
            localdate=localdate==null?LocalDate.MIN:localdate;
            LocalTime localTime = date.query(TemporalQueries.localTime());
            localTime=localTime==null?LocalTime.MIN:localTime;
            localDateTime = localdate.atTime(localTime);
        }


        this.message = localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static DateValidationCheckResult success(Map<String, Object> params) {
        return new DateValidationCheckResult(ValidationLevel.SUCCESS, params);
    }

    public static DateValidationCheckResult error(String message, ImmutableMap<String, Object> messageParams) {
        return new DateValidationCheckResult(ValidationLevel.ERROR, message, messageParams, null, null, null);
    }
}
