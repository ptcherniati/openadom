package fr.inra.oresing.domain.application.configuration.date;

import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@org.junit.jupiter.api.Tag("SUITE")
class DatePatternTest {
    public static final String DATE = "12/01/1925";
    public static final String TIME = "12:23:56";
    public static final String DATETIME = "12/01/1925 12:23:56";

    @Test
    public void testCreateWithDatePattern(){
        final DatePattern<LocalDate> localDateDatePattern = DatePattern.of("dd/MM/yyyy");
        Assert.assertNotNull(localDateDatePattern);
        final LocalDate localDate = localDateDatePattern.format(DATE);
        final String dateFormatted = localDateDatePattern.formatter().format(localDate);
        Assert.assertEquals(DATE, dateFormatted);
    }

    @Test
    public void testCreateWithBadPattern(){
        try {
            final DatePattern<LocalDate> localDateDatePattern = DatePattern.of("yyyy-Mm-dd");
        }catch (final SiOreConfigurationFormatException e){
            Assert.assertEquals(ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE,e.getException());
            Assert.assertEquals("yyyy-Mm-dd",e.getParams().get("badPattern"));
        }
    }

    @Test
    public void testCreateWithTimePattern(){
        final DatePattern<LocalTime> localTimeDatePattern = DatePattern.of("HH:mm:ss");
        Assert.assertNotNull(localTimeDatePattern);
        final LocalTime localDate = localTimeDatePattern.format(TIME);
        final String dateFormatted = localTimeDatePattern.formatter().format(localDate);
        Assert.assertEquals(TIME, dateFormatted);
    }

    @Test
    public void testCreateWithDateTimePattern(){
        final DatePattern<LocalDateTime> localTimeDatePattern = DatePattern.of("dd/MM/yyyy HH:mm:ss");
        Assert.assertNotNull(localTimeDatePattern);
        final LocalDateTime localDate = localTimeDatePattern.format(DATETIME);
        final String dateFormatted = localTimeDatePattern.formatter().format(localDate);
        Assert.assertEquals(DATETIME, dateFormatted);
    }

}