package fr.inra.oresing.domain.application.configuration.date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Tag("core.config")
class DurationTest {

    @Test
    void testDefaultDuration() {
        Duration duration = new Duration("");
        LocalDateTime date = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTimeRange range = duration.getLocalDateTimeRange(date);
        Assertions.assertEquals(date, range.getRange().lowerEndpoint());
        Assertions.assertEquals(date.plusDays(1), range.getRange().upperEndpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 DAYS", "10 HOURS", "30 MINUTES"})
    void testValidDurations(String durationString) {
        Assertions.assertTrue(Duration.isValid(durationString));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1DAY", "10H", "30M"})
    void testInvalidDurations(String durationString) {
        Assertions.assertFalse(Duration.isValid(durationString));
    }

    @Test
    void testGetLocalDateTimeRangeWithLocalDateTime() {
        Duration duration = new Duration("5 DAYS");
        LocalDateTime date = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTimeRange range = duration.getLocalDateTimeRange(date);
        Assertions.assertEquals(date, range.getRange().lowerEndpoint());
        Assertions.assertEquals(date.plusDays(5), range.getRange().upperEndpoint());
    }

    @Test
    void testGetLocalDateTimeRangeWithLocalDate() {
        Duration duration = new Duration("2 WEEKS");
        LocalDate date = LocalDate.of(2024, 1, 1);
        LocalDateTimeRange range = duration.getLocalDateTimeRange(date);
        Assertions.assertEquals(date.atStartOfDay(), range.getRange().lowerEndpoint());
        Assertions.assertEquals(date.atStartOfDay().plusWeeks(2), range.getRange().upperEndpoint());
    }
}