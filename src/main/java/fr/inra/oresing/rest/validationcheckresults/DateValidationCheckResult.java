package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Map;
import java.util.Optional;

@Value
public class DateValidationCheckResult implements ValidationCheckResult {
    ValidationLevel level;
    String message;
    Map<String, Object> messageParams;
    Object target;
    TemporalAccessor date;

    public DateValidationCheckResult(ValidationLevel level, String message, Map<String, Object> messageParams, Object target, TemporalAccessor date) {
        this.level = level;
        this.message = message;
        this.messageParams = messageParams;
        this.target = target;
        this.date = date;
    }

    public DateValidationCheckResult(ValidationLevel success, Map<String, Object> params) {
        this.messageParams = params;
        this.level = success;
        this.target = Optional.ofNullable(messageParams)
                .map(mp->mp.getOrDefault(
                        CheckerTarget.CheckerTargetType.PARAM_COLUMN.name(),
                        mp.getOrDefault(CheckerTarget.CheckerTargetType.PARAM_VARIABLE_COMPONENT_KEY.name(), null)
                ))
                .orElse(null);
        this.date = (TemporalAccessor) Optional.ofNullable(messageParams).map(mp->mp.getOrDefault(DateLineChecker.PARAM_DATE, null)).orElse(null);
        LocalDateTime localDateTime = null;
        if(date!=null){
            LocalDate localdate = date.query(TemporalQueries.localDate());
            localdate=localdate==null?LocalDate.of(1970, 1, 1):localdate;
            LocalTime localTime = date.query(TemporalQueries.localTime());
            localTime=localTime==null?LocalTime.MIN:localTime;
            localDateTime = localdate.atTime(localTime);
        }


        this.message = localDateTime==null?null:localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static DateValidationCheckResult success(Map<String, Object> params) {
        return new DateValidationCheckResult(ValidationLevel.SUCCESS, params);
    }

    public static DateValidationCheckResult error(String message, ImmutableMap<String, Object> messageParams) {
        return new DateValidationCheckResult(ValidationLevel.ERROR, message, messageParams, null, null);
    }
}