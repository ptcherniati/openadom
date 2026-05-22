package fr.inra.oresing.domain.application.configuration.date;

import fr.inra.oresing.domain.checker.type.TypeOfDate;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.application.configuration.date.DatePattern.MM_YYYY;
import static fr.inra.oresing.domain.application.configuration.date.DatePattern.YYYY;

@org.junit.jupiter.api.Tag("domain.model")
class DatePatternTest {
    public static final String DATE = "12/01/1925";
    public static final String TIME = "12:23:56";
    public static final String DATETIME = "12/01/1925 12:23:56";
    public static Stream<PatternArgument> patternArguments =
            Stream.of(
                    new PatternArgument(
                            MM_YYYY,
                            "05/2015",
                            LocalDate.class,
                            TypeOfDate.DATE
                    ),
                    new PatternArgument(
                            YYYY,
                            "2014",
                            LocalDate.class,
                            TypeOfDate.DATE
                    ),
                    new PatternArgument(
                            "dd/MM/yyyy",
                            "23/01/2014",
                            LocalDate.class,
                            TypeOfDate.DATE
                    ),
                    new PatternArgument(
                            "yyyy-MM-dd",
                            "2014-02-23",
                            LocalDate.class,
                            TypeOfDate.DATE
                    ),
                    new PatternArgument(
                            "dd/MM/yyyy HH:mm:ss",
                            "28/05/2005 23:12:54",
                            LocalDateTime.class,
                            TypeOfDate.DATETIME
                    ),
                    new PatternArgument(
                            "HH:mm:ss",
                            "18:54:05",
                            LocalTime.class,
                            TypeOfDate.TIME
                    )
            );

    @TestFactory
    @DisplayName("Testsfor different patterns")
    Stream<DynamicNode> createPatternTest() {
        return Stream.concat(
                patternArguments
                        .map(patternArgument -> DynamicTest.dynamicTest(
                                        "create pattern %s".formatted(patternArgument.pattern()),
                                        () -> {
                                            final DatePattern<TemporalAccessor> temporalAccessorDatePattern = DatePattern.of(patternArgument.pattern());
                                            final TemporalAccessor format = temporalAccessorDatePattern.format(patternArgument.dateTotest());
                                            Assertions.assertNotNull(temporalAccessorDatePattern);
                                            Assertions.assertNotNull(format);
                                            Assertions.assertEquals(format.getClass(), patternArgument.type());
                                            Assertions.assertEquals(temporalAccessorDatePattern.type(), patternArgument.type());
                                            Assertions.assertEquals(temporalAccessorDatePattern.typeOfDate(), patternArgument.typeOfDate());
                                            Assertions.assertEquals(temporalAccessorDatePattern.getFieldType(), patternArgument.typeOfDate());
                                        }
                                )
                        ),
                Stream.of(
                        DynamicTest.dynamicTest("bad pattern", () -> {
                            Assertions.assertThrows(
                                    SiOreConfigurationFormatException.class,
                                    () -> DatePattern.of("YYYY")
                            );
                        }),
                        DynamicTest.dynamicTest("bad format for pattern", () -> {
                            Assertions.assertThrows(
                                    DateTimeParseException.class,
                                    () -> DatePattern.of("dd/MM/yyyy").format("01/2014")
                            );
                        }),
                        DynamicTest.dynamicTest("start of month", () -> {
                                    final LocalDate date = (LocalDate) DatePattern.of(MM_YYYY).format("02/2014", false);
                                    Assertions.assertEquals(LocalDate.of(2014, 2, 1), date);
                                }
                        ),
                        DynamicTest.dynamicTest("end of month", () -> {
                                    final LocalDate date = (LocalDate) DatePattern.of(MM_YYYY).format("02/2014", true);
                                    Assertions.assertEquals(LocalDate.of(2014, 2, 28), date);
                                }
                        ),
                        DynamicTest.dynamicTest("start of year", () -> {
                                    final LocalDate date = (LocalDate) DatePattern.of(YYYY).format("2014", false);
                                    Assertions.assertEquals(LocalDate.of(2014, 1, 1), date);
                                }
                        ),
                        DynamicTest.dynamicTest("end of tear", () -> {
                                    final LocalDate date = (LocalDate) DatePattern.of(YYYY).format("2014", true);
                                    Assertions.assertEquals(LocalDate.of(2014, 12, 31), date);
                                }
                        )
                )
        );
    }

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

    @Test
    void testCreateWithNullKeywordThrowsMissingPattern() {
        SiOreConfigurationFormatException ex = Assertions.assertThrows(
                SiOreConfigurationFormatException.class,
                () -> DatePattern.of("null")
        );
        Assertions.assertEquals(ConfigurationException.MISSING_PATTERN_FOR_CHECKER_DATE, ex.getException());
    }

    @Test
    void dateFromStandardFormat_nullInputs_returnNull() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(p.dateFromStandardFormat(null));
        Assertions.assertNull(p.dateFromStandardFormat("null"));
    }

    @Test
    void dateFromStandardFormat_validIsoDateTime_isReformatted() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        String iso = "2024-03-15 12:34:56";
        String out = p.dateFromStandardFormat(iso);
        Assertions.assertEquals("15/03/2024", out);
    }

    @Test
    void dateFromStandardFormat_unparseableReturnsInputUnchanged() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        Assertions.assertEquals("not-a-date", p.dateFromStandardFormat("not-a-date"));
    }

    @Test
    void dateToStandardFormat_nullInputs_returnNull() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(p.dateToStandardFormat(null));
        Assertions.assertNull(p.dateToStandardFormat("null"));
    }

    @Test
    void dateToStandardFormat_dateType_returnsStandardFormat() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        String out = p.dateToStandardFormat("15/03/2024");
        Assertions.assertNotNull(out);
        Assertions.assertTrue(out.startsWith("2024-03-15"), "should be ISO-like: " + out);
    }

    @Test
    void dateToStandardFormat_timeType_returnsStandardFormat() {
        DatePattern<LocalTime> p = DatePattern.of("HH:mm:ss");
        String out = p.dateToStandardFormat("12:34:56");
        Assertions.assertNotNull(out);
        Assertions.assertTrue(out.contains("12:34:56"), "should contain time: " + out);
    }

    @Test
    void dateToStandardFormat_datetimeType_returnsStandardFormat() {
        DatePattern<LocalDateTime> p = DatePattern.of("dd/MM/yyyy HH:mm:ss");
        String out = p.dateToStandardFormat("15/03/2024 12:34:56");
        Assertions.assertNotNull(out);
        Assertions.assertTrue(out.startsWith("2024-03-15"), "should be ISO-like: " + out);
    }

    @Test
    void dateToStandardFormat_unparseableButStandard_returnsAsIs() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        // déjà au format standard → retourné tel quel
        String standard = "2024-03-15 12:34:56";
        Assertions.assertEquals(standard, p.dateToStandardFormat(standard));
    }

    @Test
    void dateToStandardFormat_unparseable_throws() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        Assertions.assertThrows(
                fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException.class,
                () -> p.dateToStandardFormat("garbage"));
    }

    @Test
    void getFieldType_byTypeClass() {
        Assertions.assertEquals(TypeOfDate.DATE, DatePattern.of("dd/MM/yyyy").getFieldType());
        Assertions.assertEquals(TypeOfDate.TIME, DatePattern.of("HH:mm:ss").getFieldType());
        Assertions.assertEquals(TypeOfDate.DATETIME, DatePattern.of("dd/MM/yyyy HH:mm:ss").getFieldType());
    }

    @Test
    void format_emptyOrNullDate_returnsNullForLocalDate() {
        DatePattern<LocalDate> p = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(p.format(""));
        Assertions.assertNull(p.format((String) null));
    }

    record PatternArgument(
            String pattern,
            String dateTotest,
            Class<? extends TemporalAccessor> type,
            TypeOfDate typeOfDate) {
    }

}