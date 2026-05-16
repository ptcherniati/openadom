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

@Tag("domain.model")
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

    record PatternArgument(
            String pattern,
            String dateTotest,
            Class<? extends TemporalAccessor> type,
            TypeOfDate typeOfDate) {
    }

    @Test
    @DisplayName("dateToStandardFormat — date format dd/MM/yyyy produces ISO-like output")
    void dateToStandardFormatDate() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        String result = dp.dateToStandardFormat("21/06/2004");
        Assertions.assertNotNull(result);
        // Standard format is yyyy-MM-dd HH:mm:ss
        Assertions.assertTrue(result.startsWith("2004-06-21"), "Expected ISO-format date, got: " + result);
    }

    @Test
    @DisplayName("dateToStandardFormat — null returns null")
    void dateToStandardFormatNull() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(dp.dateToStandardFormat(null));
    }

    @Test
    @DisplayName("dateToStandardFormat — 'null' string returns null")
    void dateToStandardFormatNullString() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(dp.dateToStandardFormat("null"));
    }

    @Test
    @DisplayName("dateToStandardFormat — already in standard format passes through")
    void dateToStandardFormatAlreadyStandard() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        // A string in the standard format should be returned as-is (DateTimeParseException path)
        String standard = "2024-06-21 00:00:00";
        String result = dp.dateToStandardFormat(standard);
        Assertions.assertEquals(standard, result);
    }

    @Test
    @DisplayName("dateFromStandardFormat — null returns null")
    void dateFromStandardFormatNull() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(dp.dateFromStandardFormat(null));
    }

    @Test
    @DisplayName("dateFromStandardFormat — 'null' string returns null")
    void dateFromStandardFormatNullString() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        Assertions.assertNull(dp.dateFromStandardFormat("null"));
    }

    @Test
    @DisplayName("dateFromStandardFormat — standard format converted to pattern format")
    void dateFromStandardFormatConverts() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        String result = dp.dateFromStandardFormat("2004-06-21 00:00:00");
        Assertions.assertEquals("21/06/2004", result);
    }

    @Test
    @DisplayName("dateFromStandardFormat — non-parseable string returned as-is")
    void dateFromStandardFormatPassThrough() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        String result = dp.dateFromStandardFormat("21/06/2004");
        // not a standard format string, should be returned as-is
        Assertions.assertEquals("21/06/2004", result);
    }

    @Test
    @DisplayName("format(null) returns null for non-MM/YYYY and non-YYYY patterns")
    void formatNullDateReturnsNull() {
        DatePattern<LocalDate> dp = DatePattern.of("dd/MM/yyyy");
        LocalDate result = dp.format(null);
        Assertions.assertNull(result);
    }

}