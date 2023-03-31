package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.*;
import java.util.stream.Collectors;

@Value
public class DateValidationCheckResult implements ValidationCheckResult {
    ValidationLevel level;
    String message;
    Map<String, Object> messageParams;
    CheckerTarget target;
    List<TemporalAccessor> date;
    SortedSet<LocalDateTime> localDateTime;

    public static DateValidationCheckResult success(CheckerTarget target, List<TemporalAccessor> dates) {
        final SortedSet<LocalDateTime> datesTime = dates.stream()
                .map(date -> {
                            LocalDate localdate = date.query(TemporalQueries.localDate());
                            localdate = localdate == null ? LocalDate.of(1970, 1, 1) : localdate;
                            LocalTime localTime = date.query(TemporalQueries.localTime());
                            localTime = localTime == null ? LocalTime.MIN : localTime;
                            LocalDateTime localDateTime = localdate.atTime(localTime);
                            return localDateTime;
                        }
                )
                .collect(Collectors.toCollection(TreeSet::new));
        return new DateValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, dates, datesTime);
    }

    public static DateValidationCheckResult error(CheckerTarget target, String message, ImmutableMap<String, Object> messageParams) {
        return new DateValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, null, null);
    }
}