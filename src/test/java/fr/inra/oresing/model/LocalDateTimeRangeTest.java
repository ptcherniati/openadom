package fr.inra.oresing.model;

import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.DateLineCheckerConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.time.Year;

public class LocalDateTimeRangeTest {

    @Test
    public void testToSqlExpression() {
        {
            LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.forYear(Year.of(2020));
            String sql = localDateTimeRange.toSqlExpression();
            Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\")", sql);
            LocalDateTimeRange parsed = LocalDateTimeRange.parseSql(sql);
            Assert.assertEquals(localDateTimeRange, parsed);
        }
        {
            LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.always();
            String sql = localDateTimeRange.toSqlExpression();
            Assert.assertEquals("(,)", sql);
            LocalDateTimeRange parsed = LocalDateTimeRange.parseSql(sql);
            Assert.assertEquals(localDateTimeRange, parsed);
        }
    }
    @Test
    public void testDayPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/01/2020", new DateLineChecker(null, "dd/MM/yyyy", getDateCheckerConfiguration("2 MONTHS")));
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-03-01 00:00:00\")", range.toSqlExpression());
         range = LocalDateTimeRange.parse("01/01/2020", new DateLineChecker(null, "dd/MM/yyyy", null));
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-01-02 00:00:00\")", range.toSqlExpression());
    }
    @Test
    public void testSemiHourlyPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/01/2020 01:30:00", new DateLineChecker(null, "dd/MM/yyyy HH:mm:ss", getDateCheckerConfiguration("30 MINUTES")));
        Assert.assertEquals("[\"2020-01-01 01:30:00\",\"2020-01-01 02:00:00\")", range.toSqlExpression());
         range = LocalDateTimeRange.parse("01/01/2020 01:30:00", new DateLineChecker(null, "dd/MM/yyyy HH:mm:ss", null));
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-01-02 00:00:00\")", range.toSqlExpression());
    }
    @Test
    public void testMounthPattern() {
        LocalDateTimeRange range = LocalDateTimeRange.parse("01/2020", new DateLineChecker(null, "MM/yyyy", getDateCheckerConfiguration("2 MONTHS")));
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-03-01 00:00:00\")", range.toSqlExpression());
         range = LocalDateTimeRange.parse("01/2020", new DateLineChecker(null, "MM/yyyy", null));
        Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-02-01 00:00:00\")", range.toSqlExpression());
    }

    private DateLineCheckerConfiguration getDateCheckerConfiguration(String duration) {
        return new DateLineCheckerConfiguration() {
            @Override
            public String getPattern() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public String getDuration() {
                return duration;
            }

            @Override
            public boolean isCodify() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public boolean isRequired() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public String getGroovy() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public String getReferences() {
                throw new UnsupportedOperationException("doublure de test");
            }
        };
    }
}