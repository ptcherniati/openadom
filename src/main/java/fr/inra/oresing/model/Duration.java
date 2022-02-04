package fr.inra.oresing.model;

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

    public Duration(String duration) {
        final Matcher matcher = PATTERN.matcher(duration);
        if(matcher.find()){
            this.amount = Long.parseLong( matcher.group(1));
            this.temporalUnit = ChronoUnit.valueOf(matcher.group(2).toUpperCase());
        }
    }
    LocalDateTimeRange getLocalDateTimeRange(LocalDateTime date){
        return LocalDateTimeRange.between(LocalDateTime.from(date), date.plus(amount, temporalUnit));
    }
    LocalDateTimeRange getLocalDateTimeRange(LocalDate date){
        return LocalDateTimeRange.between(LocalDateTime.from(date.atStartOfDay()), date.atStartOfDay().plus(amount,temporalUnit));
    }
}