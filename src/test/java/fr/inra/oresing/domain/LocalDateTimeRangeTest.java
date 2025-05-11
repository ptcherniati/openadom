package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.checker.type.DateType;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

@org.junit.jupiter.api.Tag("domain.model")
public class LocalDateTimeRangeTest {

    private static DateType getDateCheckerConfiguration(final String pattern, final String duration) {
        if ("MM/yyyy".equals(pattern)) {
            return new DateType(pattern, DateTimeFormatter.ofPattern(pattern), duration, null, null);
        }
        if ("yyyy".equals(pattern)) {
            return new DateType(pattern, DateTimeFormatter.ofPattern(pattern), duration, null, null);
        }
        final DatePattern<TemporalAccessor> datePattern = DatePattern.of(pattern);
        return new DateType(datePattern.pattern(), datePattern.formatter(), duration, null, null);
    }

    /*@Test
    public void testToSqlExpression() {
        {
            final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.forYear(Year.of(2020));
            final String sql = localDateTimeRange.toSqlExpression();
            assertEquals("[\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\")", sql);
            final LocalDateTimeRange parsed = LocalDateTimeRange.parseSql(sql);
            assertEquals(localDateTimeRange, parsed);
        }
        {
            final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.always();
            final String sql = localDateTimeRange.toSqlExpression();
            assertEquals("(,)", sql);
            final LocalDateTimeRange parsed = LocalDateTimeRange.parseSql(sql);
            assertEquals(localDateTimeRange, parsed);
        }
    }*/
    @Test
    public void testDayPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/01/2020", getDateCheckerConfiguration("dd/MM/yyyy", "2 MONTHS"));
        assertEquals("[\"2020-01-01 00:00:00\",\"2020-03-01 00:00:00\")", range.toSqlExpression());
        range = LocalDateTimeRange.parse("01/01/2020", getDateCheckerConfiguration("dd/MM/yyyy", null));
        assertEquals("[\"2020-01-01 00:00:00\",\"2020-01-02 00:00:00\")", range.toSqlExpression());
    }

    @Test
    public void testSemiHourlyPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/01/2020 01:30:00", getDateCheckerConfiguration("dd/MM/yyyy HH:mm:ss", "30 MINUTES"));
        assertEquals("[\"2020-01-01 01:30:00\",\"2020-01-01 02:00:00\")", range.toSqlExpression());
        range = LocalDateTimeRange.parse("01/01/2020 01:30:00", getDateCheckerConfiguration("dd/MM/yyyy HH:mm:ss", null));
        assertEquals("[\"2020-01-01 00:00:00\",\"2020-01-02 00:00:00\")", range.toSqlExpression());
    }

    @Test
    public void testMounthPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/2020", getDateCheckerConfiguration("MM/yyyy", "2 MONTHS"));
        assertEquals("[\"2020-01-01 00:00:00\",\"2020-03-01 00:00:00\")", range.toSqlExpression());
        range = LocalDateTimeRange.parse("01/2020", getDateCheckerConfiguration("MM/yyyy", null));
        assertEquals("[\"2020-01-01 00:00:00\",\"2020-02-01 00:00:00\")", range.toSqlExpression());
    }
}