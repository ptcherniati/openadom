package fr.inra.oresing.domain.application.configuration.date;

import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@org.junit.jupiter.api.Tag("domain.model")
class DatePatternTest {
    public static final String DATE = "12/01/1925";
    public static final String TIME = "12:23:56";
    public static final String DATETIME = "12/01/1925 12:23:56";

    @Test
     void testCreateWithDatePattern() {
        final DatePattern<LocalDate> localDateDatePattern = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNotNull(localDateDatePattern);
        final LocalDate localDate = localDateDatePattern.format(DATE);
        final String dateFormatted = localDateDatePattern.formatter().format(Objects.requireNonNull(localDate));
        Assertions.assertEquals(DATE, dateFormatted);
    }

    @Test
     void testCreateWithBadPattern() {
        try {
            final DatePattern<LocalDate> localDateDatePattern = DatePattern.of("yyyy-Mm-dd");
        } catch (final SiOreConfigurationFormatException e) {
            Assertions.assertEquals(ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE, e.getException());
            Assertions.assertEquals("yyyy-Mm-dd", e.getParams().get("badPattern"));
        }
    }

    @Test
     void testCreateWithTimePattern() {
        final DatePattern<LocalTime> localTimeDatePattern = DatePattern.of("HH:mm:ss");
        Assertions.assertNotNull(localTimeDatePattern);
        final LocalTime localDate = localTimeDatePattern.format(TIME);
        final String dateFormatted = localTimeDatePattern.formatter().format(Objects.requireNonNull(localDate));
        Assertions.assertEquals(TIME, dateFormatted);
    }

    @Test
     void testCreateWithDateTimePattern() {
        final DatePattern<LocalDateTime> localTimeDatePattern = DatePattern.of("dd/MM/yyyy HH:mm:ss");
        Assertions.assertNotNull(localTimeDatePattern);
        final LocalDateTime localDate = localTimeDatePattern.format(DATETIME);
        final String dateFormatted = localTimeDatePattern.formatter().format(Objects.requireNonNull(localDate));
        Assertions.assertEquals(DATETIME, dateFormatted);
    }

}