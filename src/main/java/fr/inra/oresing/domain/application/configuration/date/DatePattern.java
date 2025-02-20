package fr.inra.oresing.domain.application.configuration.date;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.checker.type.TypeOfDate;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

public record DatePattern<T extends TemporalAccessor>(String pattern, DateTimeFormatter formatter, Class<T> type) {

    public static final String MM_YYYY = "MM/yyyy";
    public static final String YYYY = "yyyy";

    public static <T extends TemporalAccessor> DatePattern<T> of(final String pattern) {
        switch (pattern) {
            case MM_YYYY -> {
                return (DatePattern<T>) new DatePattern<>(MM_YYYY, DateTimeFormatter.ofPattern(MM_YYYY), LocalDate.class);
            }
            case YYYY -> {
                return (DatePattern<T>) new DatePattern<>(YYYY, DateTimeFormatter.ofPattern(YYYY), LocalDate.class);
            }
            case "null" ->
                    throw new SiOreConfigurationFormatException(ConfigurationException.MISSING_PATTERN_FOR_CHECKER_DATE, Map.of());
            default -> {
                String NOW;
                Class<T> type;
                DateTimeFormatter dateTimeFormatter;
                try {
                    dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
                    NOW = dateTimeFormatter.format(LocalDateTime.now());
                } catch (final DateTimeParseException | IllegalArgumentException e) {
                    throw new SiOreConfigurationFormatException(
                            ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,
                            Map.of("badPattern", pattern)
                    );
                }
                try {
                    final LocalDateTime localDateTime = LocalDateTime.parse(NOW, dateTimeFormatter);
                    type = (Class<T>) localDateTime.getClass();
                } catch (final DateTimeParseException | IllegalArgumentException dte) {
                    try {
                        final LocalDate localDate = LocalDate.parse(NOW, dateTimeFormatter);
                        type = (Class<T>) localDate.getClass();
                    } catch (final DateTimeParseException | IllegalArgumentException de) {
                        try {
                            final LocalTime localTime = LocalTime.parse(NOW, dateTimeFormatter);
                            type = (Class<T>) localTime.getClass();
                        } catch (final DateTimeParseException | IllegalArgumentException te) {
                            throw new SiOreConfigurationFormatException(
                                    ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,
                                    Map.of("badPattern", pattern)
                            );
                        }
                    }
                }
                return new DatePattern<>(pattern, dateTimeFormatter, type);
            }
        }
    }

    public T format(final String dateToFormat) {
        if (Strings.isNullOrEmpty(dateToFormat)) {
            return null;
        }
        if (LocalTime.class.equals(type())) {
            return (T) LocalTime.parse(dateToFormat, formatter);
        }
        if (LocalDate.class.equals(type())) {
            return (T) LocalDate.parse(dateToFormat, formatter);
        }
        if (LocalDateTime.class.equals(type())) {
            return (T) LocalDateTime.parse(dateToFormat, formatter);
        }
        throw new IllegalArgumentException("illegal type of date");
    }

    public TypeOfDate getFieldType() {
        final DatePattern<TemporalAccessor> datePattern = of(pattern());
        if (LocalTime.class.equals(datePattern.type())) {
            return TypeOfDate.TIME;
        }
        if (LocalDate.class.equals(datePattern.type())) {
            return TypeOfDate.DATE;
        }
        if (LocalDateTime.class.equals(datePattern.type())) {
            return TypeOfDate.DATETIME;
        }
        throw new SiOreConfigurationFormatException(
                ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,
                Map.of("badPattern", pattern)
        );
    }
}
