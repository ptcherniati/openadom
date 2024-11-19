package fr.inra.oresing.domain.application.configuration.date;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Duration {
    public static final Pattern PATTERN = Pattern.compile("([0-9]*) (NANOS|MICROS|MILLIS|SECONDS|MINUTES|HOURS|HALF_DAYS|DAYS|WEEKS|MONTHS|YEARS)", Pattern.CASE_INSENSITIVE);
    long  amount = 1;
    TemporalUnit temporalUnit = ChronoUnit.DAYS;

    public Duration(final String duration) {
        super();
        Matcher matcher = PATTERN.matcher(duration);
        if (matcher.find()) {
            amount = Long.parseLong(matcher.group(1));
            temporalUnit = ChronoUnit.valueOf(matcher.group(2).toUpperCase());
        }
    }

    public static boolean isValid(final String duration) {
        return PATTERN.matcher(duration).matches();
    }

    LocalDateTimeRange getLocalDateTimeRange(final LocalDateTime date){
        return LocalDateTimeRange.between(LocalDateTime.from(date), date.plus(amount, temporalUnit));
    }
    LocalDateTimeRange getLocalDateTimeRange(final LocalDate date){
        return LocalDateTimeRange.between(LocalDateTime.from(date.atStartOfDay()), date.atStartOfDay().plus(amount,temporalUnit));
    }
}