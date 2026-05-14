package fr.inra.oresing.domain.application.configuration.date;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.checker.type.TypeOfDate;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Map;
import java.util.Optional;

public record DatePattern<T extends TemporalAccessor>(
        String pattern,
        DateTimeFormatter formatter,
        Class<T> type,
        TypeOfDate typeOfDate
) {

    public static final String MM_YYYY = "MM/yyyy";
    public static final String YYYY = "yyyy";
    public static final String DD_MM_YYYY = "dd/MM/yyyy";
    // DEFAULT est volontairement wildcard : la méthode factory of() retourne différents T selon le pattern
    public static final DatePattern<?> DEFAULT = DatePattern.of(DD_MM_YYYY);

    public static <T extends TemporalAccessor> DatePattern<T> of(final String pattern) {
        switch (pattern) {
            case "null" ->
                    throw new SiOreConfigurationFormatException(ConfigurationException.MISSING_PATTERN_FOR_CHECKER_DATE, Map.of());
            case MM_YYYY -> {
                return (DatePattern<T>) new DatePattern<>(MM_YYYY, DateTimeFormatter.ofPattern(MM_YYYY), LocalDate.class, TypeOfDate.DATE);
            }
            case YYYY -> {
                return (DatePattern<T>) new DatePattern<>(YYYY, DateTimeFormatter.ofPattern(YYYY), LocalDate.class, TypeOfDate.DATE);
            }
            default -> {
                String now;
                Class<T> type;
                DateTimeFormatter dateTimeFormatter;
                TypeOfDate typeOfDate;
                try {
                    dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
                    now = dateTimeFormatter.format(LocalDateTime.now());
                } catch (final DateTimeParseException | IllegalArgumentException e) {
                    throw new SiOreConfigurationFormatException(
                            ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,
                            Map.of("badPattern", pattern)
                    );
                }
                try {
                    final LocalDateTime localDateTime = LocalDateTime.parse(now, dateTimeFormatter);
                    type = (Class<T>) localDateTime.getClass();
                    typeOfDate = TypeOfDate.DATETIME;
                } catch (final DateTimeParseException | IllegalArgumentException dte) {
                    try {
                        final LocalDate localDate = LocalDate.parse(now, dateTimeFormatter);
                        type = (Class<T>) localDate.getClass();
                        typeOfDate = TypeOfDate.DATE;
                    } catch (final DateTimeParseException | IllegalArgumentException de) {
                        try {
                            final LocalTime localTime = LocalTime.parse(now, dateTimeFormatter);
                            type = (Class<T>) localTime.getClass();
                            typeOfDate = TypeOfDate.TIME;
                        } catch (final DateTimeParseException | IllegalArgumentException te) {
                            throw new SiOreConfigurationFormatException(
                                    ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,
                                    Map.of("badPattern", pattern)
                            );
                        }
                    }
                }
                return new DatePattern<>(pattern, dateTimeFormatter, type, typeOfDate);
            }
        }
    }

    public T format(final String dateToFormat, boolean isEnd) {
        final DatePattern<LocalDate> temporalAccessorDatePattern = DatePattern.of(DD_MM_YYYY);
        if (MM_YYYY.equals(pattern())) {
            LocalDate date = temporalAccessorDatePattern.format("01/%s".formatted(dateToFormat));
            if (isEnd && date != null) {
                return (T) date.with(TemporalAdjusters.lastDayOfMonth());
            }
            return (T) date;
        } else if (YYYY.equals(pattern())) {
            LocalDate date = temporalAccessorDatePattern.format("01/01/%s".formatted(dateToFormat));
            if (isEnd && date != null) {
                return (T) date.with(TemporalAdjusters.lastDayOfYear());
            }
            return (T) date;
        } else {
            return format(dateToFormat);
        }
    }

    public T format(final String dateToFormat) {
        final DatePattern<LocalDate> temporalAccessorDatePattern = DatePattern.of(DD_MM_YYYY);
        if (MM_YYYY.equals(pattern())) {
            return (T) temporalAccessorDatePattern.format("01/%s".formatted(dateToFormat));
        }
        if (YYYY.equals(pattern())) {
            return (T) temporalAccessorDatePattern.format("01/01/%s".formatted(dateToFormat));
        }
        if (Strings.isNullOrEmpty(dateToFormat)) {
            return null;
        }
        return switch (typeOfDate()) {
            case TIME -> (T) LocalTime.parse(dateToFormat, formatter);
            case DATE -> (T) LocalDate.parse(dateToFormat, formatter);
            case DATETIME -> (T) LocalDateTime.parse(dateToFormat, formatter);
            case null, default -> throw new IllegalArgumentException("illegal type of date");
        };
    }


    public TypeOfDate getFieldType() {
        if (LocalTime.class.equals(type())) {
            return TypeOfDate.TIME;
        }
        if (LocalDate.class.equals(type())) {
            return TypeOfDate.DATE;
        }
        return TypeOfDate.DATETIME;
    }

    public String dateFromStandardFormat(String dateString) throws SiOreIllegalArgumentException {
        if(dateString==null || "null".equals(dateString)){
            return null;
        }
        try {
            return formatter()
                    .format(
                            LocalDateTime.parse(
                                    dateString,
                                    LocalDateTimeRange.DATE_TIME_FORMATTER
                            )
                    );
        } catch (DateTimeParseException | UnsupportedTemporalTypeException ex) {
            return dateString;
        }
    }

    public String dateToStandardFormat(String dateString) throws SiOreIllegalArgumentException {
        if(dateString==null || "null".equals(dateString)){
            return null;
        }
        try {
            return Optional.ofNullable(dateString)
                    .map(date -> switch (typeOfDate()) {
                        case DATE -> LocalDate.parse(dateString, formatter()).atStartOfDay();
                        case TIME -> LocalTime.parse(dateString, formatter()).atDate(LocalDate.EPOCH);
                        case DATETIME -> LocalDateTime.parse(dateString, formatter());
                    })
                    .map(LocalDateTimeRange.DATE_TIME_FORMATTER::format)
                    .orElse(dateString);
        } catch (DateTimeParseException dpe) {
            if(LocalDateTimeRange.testIsStandardDate(dateString)) {
                return dateString;
            }
            throw new SiOreIllegalArgumentException("BAD_DATE_FORMAT", Map.of(
                    "date", dateString,
                    "format", pattern()
            ));
        }
    }
}