package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.DateType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;


public record DateValidationCheckResult(ValidationLevel level, String message, Map<String, Object> messageParams,
                                        CheckerTarget target, List<TemporalAccessor> date,
                                        SortedSet<LocalDateTime> localDateTime,
                                        DateType value) implements CheckerValidationCheckResult<DateType> {
    public static DateValidationCheckResult success(final CheckerTarget target, final List<TemporalAccessor> dates, final DateType value) {
        SortedSet<LocalDateTime> datesTime = dates.stream()
                .map(date -> {
                            LocalDate localdate = date.query(TemporalQueries.localDate());
                            localdate = localdate == null ? LocalDate.of(1970, 1, 1) : localdate;
                            LocalTime localTime = date.query(TemporalQueries.localTime());
                            localTime = localTime == null ? LocalTime.MIN : localTime;
                            return localdate.atTime(localTime);
                        }
                )
                .collect(Collectors.toCollection(TreeSet::new));
        return new DateValidationCheckResult(ValidationLevel.SUCCESS, null, null, target, dates, datesTime, (DateType) value.copy());
    }

    public static DateValidationCheckResult error(final CheckerTarget target, final String message, final ImmutableMap<String, Object> messageParams, final DateType value) {
        return new DateValidationCheckResult(ValidationLevel.ERROR, message, messageParams, target, null, null, value);
    }
}