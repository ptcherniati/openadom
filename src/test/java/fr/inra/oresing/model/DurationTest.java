package fr.inra.oresing.model;

import org.junit.Assert;
import org.junit.Test;

public class DurationTest {

    @Test
    public void testIsValidWithValidDuration() {
        Assert.assertTrue(Duration.isValid("2 MONTHS"));
        Assert.assertTrue(Duration.isValid("1 DAYS"));
        Assert.assertTrue(Duration.isValid("30 MINUTES"));
        Assert.assertTrue(Duration.isValid("1 YEARS"));
        Assert.assertTrue(Duration.isValid("3 HOURS"));
        Assert.assertTrue(Duration.isValid("1 WEEKS"));
    }

    @Test
    public void testIsValidWithInvalidDuration() {
        Assert.assertFalse(Duration.isValid("invalid"));
        Assert.assertFalse(Duration.isValid("MONTHS"));
        Assert.assertFalse(Duration.isValid("2MONTHS"));
        Assert.assertFalse(Duration.isValid(""));
    }

    @Test
    public void testPatternIsCaseInsensitive() {
        Assert.assertTrue(Duration.isValid("2 months"));
        Assert.assertTrue(Duration.isValid("1 days"));
        Assert.assertTrue(Duration.isValid("5 Minutes"));
    }

    @Test
    public void testGetLocalDateTimeRangeForDate() {
        Duration duration = new Duration("1 MONTHS");
        java.time.LocalDate date = java.time.LocalDate.of(2020, 1, 1);
        LocalDateTimeRange range = duration.getLocalDateTimeRange(date);
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-02-01 00:00:00\")", range.toSqlExpression());
    }

    @Test
    public void testGetLocalDateTimeRangeForDateTime() {
        Duration duration = new Duration("2 HOURS");
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.of(2020, 6, 15, 10, 0, 0);
        LocalDateTimeRange range = duration.getLocalDateTimeRange(dateTime);
        Assert.assertEquals("[\"2020-06-15 10:00:00\",\"2020-06-15 12:00:00\")", range.toSqlExpression());
    }
}
