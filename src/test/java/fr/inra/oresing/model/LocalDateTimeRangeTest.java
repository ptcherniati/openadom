package fr.inra.oresing.model;

import org.junit.Assert;
import org.junit.Test;

import java.time.Year;

public class LocalDateTimeRangeTest {

    @Test
    public void testToSqlExpression() {
        {
            LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.forYear(Year.of(2020));
            String sql = localDateTimeRange.toSqlExpression();
            Assert.assertEquals("[\"2020-01-01 00:00:00\",\"2020-12-31 00:00:00\")", sql);
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
}