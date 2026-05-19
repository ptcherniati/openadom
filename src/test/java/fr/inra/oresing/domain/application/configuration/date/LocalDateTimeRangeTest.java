package fr.inra.oresing.domain.application.configuration.date;

import com.google.common.collect.BoundType;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange.ACCEPTED_START_OF_BOUNDS;

@Slf4j
@Tag("core.config")
class LocalDateTimeRangeTest {
    public static final String SQL_INTERVAL = "[\"2024-01-01 23:54:00\",\"2024-03-12 20:55:00\")";
    public static final LocalDateTime FROM = LocalDateTime.of(2024, 1, 1, 23, 54, 0);
    public static final LocalDateTime TO = LocalDateTime.of(2024, 3, 12, 20, 55, 0);
    public static final Stream<IntervalDescription> INTERVAL_DESCRIPTIONS = Stream.of(
            new IntervalDescription(
                    DatePattern.DD_MM_YYYY,
                    "21/06/2004",
                    "23/12/2005",
                    "2004-06-21T00:00:00",
                    "2005-12-23T00:00:00"
            ),
            new IntervalDescription(
                    "dd/MM/yyyy HH:mm:ss",
                    "21/06/2004 23:54:32",
                    "23/12/2005 21:54:01",
                    "2004-06-21T23:54:32",
                    "2005-12-23T21:54:01"
            ),
            new IntervalDescription(
                    "HH:mm:ss",
                    "21:54:32",
                    "23:54:01",
                    "1970-01-01T21:54:32",
                    "1970-01-01T23:54:01"
            ),
            new IntervalDescription(
                    DatePattern.MM_YYYY,
                    "06/2004",
                    "12/2005",
                    "2004-06-01T00:00:00",
                    "2005-12-31T00:00:00"
            ),
            new IntervalDescription(
                    DatePattern.YYYY,
                    "2004",
                    "2005",
                    "2004-01-01T00:00:00",
                    "2005-12-31T00:00:00"
            )
    );
    public static LocalDateTimeRange instance = new LocalDateTimeRange(
            List.of(
                    FROM,
                    TO
            )
    );

    @Test
    void getError() {
        final String boundValue = "23/01/2004";
        final String lowerBound = "01/01/2004";
        final String upperBound = "31/12/2004";
        final SiOreIllegalArgumentException error = LocalDateTimeRange.getError(
                boundValue,
                lowerBound,
                upperBound,
                ACCEPTED_START_OF_BOUNDS
        );
        Assertions.assertEquals("badBoundsForInterval", error.getMessage());
        final Map<String, Object> params = error.getParams();
        Assertions.assertEquals(boundValue, params.get("boundValue"));
        Assertions.assertEquals(lowerBound, params.get("lowerBound"));
        Assertions.assertEquals(upperBound, params.get("upperBound"));
        Assertions.assertEquals(ACCEPTED_START_OF_BOUNDS, params.get("acceptedValues"));

    }

    @Test
    void getErrorBoundType() {
        final SiOreIllegalArgumentException error = LocalDateTimeRange.getErrorBoundType(BoundType.CLOSED);
        Assertions.assertEquals("badBoundTypeForInterval", error.getMessage());
        final Map<String, Object> params = error.getParams();
        Assertions.assertEquals(BoundType.CLOSED, params.get("boundType"));
        org.assertj.core.api.Assertions.assertThat((Set) params.get("knownBoundType"))
                .containsExactlyInAnyOrder(BoundType.CLOSED.name(), BoundType.OPEN.name());
    }

    @Test
    void always() {
        final LocalDateTimeRange always = LocalDateTimeRange.always();
        Assertions.assertFalse(always.getRange().hasLowerBound());
        Assertions.assertFalse(always.getRange().hasUpperBound());
    }

    @Test
    void forYear() {
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.forYear(Year.of(2024));
        final LocalDateTime yearStart = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        Assertions.assertEquals(yearStart, localDateTimeRange.getRange().lowerEndpoint());
        Assertions.assertEquals(yearStart
                .with(TemporalAdjusters.firstDayOfNextYear()), localDateTimeRange.getRange().upperEndpoint());
    }

    @Test
    void forDay() {
        final LocalDate localDate = LocalDate.of(2024, 1, 25);
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.forDay(localDate);
        Assertions.assertEquals(localDate.atStartOfDay(), localDateTimeRange.getRange().lowerEndpoint());
        Assertions.assertEquals(localDate.atStartOfDay().plusDays(1), localDateTimeRange.getRange().upperEndpoint());
    }

    @Test
    void between() {
        final LocalDate start = LocalDate.of(2024, 1, 1);
        final LocalDate end = LocalDate.of(2025, 1, 1);
        final LocalDateTimeRange between = LocalDateTimeRange.between(start, end);
        Assertions.assertEquals(LocalDateTimeRange.forYear(Year.of(2024)), between);
    }

    @Test
    void since() {
        final LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDateTimeRange since = LocalDateTimeRange.since(start);
        Assertions.assertEquals(start.atStartOfDay(), since.getRange().lowerEndpoint());
        Assertions.assertFalse(since.getRange().hasUpperBound());
    }

    @Test
    void until() {
        final LocalDate end = LocalDate.of(2024, 1, 1);
        LocalDateTimeRange until = LocalDateTimeRange.until(end);
        Assertions.assertFalse(until.getRange().hasLowerBound());
        Assertions.assertEquals(end.atStartOfDay(), until.getRange().upperEndpoint());
    }

    @Test
    void toSqlExpression() {
        final String sqlExpression = instance.toSqlExpression();
        Assertions.assertEquals(SQL_INTERVAL, sqlExpression);
    }

    @Test
    void getKnownPatterns() {
        Assertions.assertEquals(LocalDateTimeRange.KNOWN_PATTERNS, LocalDateTimeRange.getKnownPatterns());
    }

    @Test
    void parse() {
        DateType dateType = DateType.of("2024-01-01");
        final LocalDate day = LocalDate.of(2024, 1, 1);
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.parse(day.atStartOfDay(), dateType);
        Assertions.assertEquals(LocalDateTimeRange.forDay(day), localDateTimeRange);
    }

    @Test
    void parseSql() {
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.parseSql(SQL_INTERVAL);
        Assertions.assertEquals(FROM, localDateTimeRange.getRange().lowerEndpoint());
        Assertions.assertEquals(TO, localDateTimeRange.getRange().upperEndpoint());
    }

    @TestFactory
    @DisplayName("Tests for different patterns of date")
    Stream<DynamicNode> buildLocalDateIntervalWithPattern() {
        return INTERVAL_DESCRIPTIONS
                .map(intervalDescription -> {
                    final DatePattern<TemporalAccessor> datePattern = DatePattern.of(intervalDescription.pattern());
                    return DynamicContainer.dynamicContainer(
                            "buildIntervals with pattern %s".formatted(intervalDescription.pattern()),
                            Stream.of(
                                    DynamicTest.dynamicTest(
                                            "buildIntervals with Strings".formatted(intervalDescription.pattern()), () -> {
                                                final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.of(datePattern, intervalDescription.from(), intervalDescription.to());
                                                Assertions.assertNotNull(localDateTimeRange);
                                                Assertions.assertEquals(
                                                        intervalDescription.fromDate(),
                                                        DateTimeFormatter.ISO_DATE_TIME.format(localDateTimeRange.getRange().lowerEndpoint())
                                                );
                                                Assertions.assertEquals(
                                                        intervalDescription.toDate(),
                                                        DateTimeFormatter.ISO_DATE_TIME.format(localDateTimeRange.getRange().upperEndpoint())
                                                );
                                            }
                                    ),
                                    DynamicTest.dynamicTest(
                                            "buildIntervals with dates".formatted(intervalDescription.pattern()), () -> {
                                                final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.of(
                                                        datePattern,
                                                        datePattern.format(intervalDescription.from()),
                                                        datePattern.format(intervalDescription.to(), true)
                                                );
                                                Assertions.assertNotNull(localDateTimeRange);
                                                Assertions.assertEquals(
                                                        intervalDescription.fromDate(),
                                                        DateTimeFormatter.ISO_DATE_TIME.format(localDateTimeRange.getRange().lowerEndpoint())
                                                );
                                                Assertions.assertEquals(
                                                        intervalDescription.toDate(),
                                                        DateTimeFormatter.ISO_DATE_TIME.format(localDateTimeRange.getRange().upperEndpoint())
                                                );
                                            }
                                    )
                            )
                    );
                });
    }

    record IntervalDescription(
            String pattern,
            String from,
            String to,
            String fromDate,
            String toDate
    ) {

    }

    // =========================================================================
    //  Méthodes non couvertes : since/until(LocalDateTime), testIsStandardDate,
    //  parseSql variantes, getLowerPointOrMin / getUpperEndpointOrMax
    // =========================================================================

    @Test
    @DisplayName("since(LocalDateTime) crée un range sans borne supérieure")
    void sinceLocalDateTime() {
        LocalDateTime dt = LocalDateTime.of(2020, 6, 1, 12, 0, 0);
        LocalDateTimeRange range = LocalDateTimeRange.since(dt);
        Assertions.assertEquals(dt, range.getRange().lowerEndpoint());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("until(LocalDateTime) crée un range sans borne inférieure")
    void untilLocalDateTime() {
        LocalDateTime dt = LocalDateTime.of(2021, 6, 1, 23, 59, 59);
        LocalDateTimeRange range = LocalDateTimeRange.until(dt);
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertEquals(dt, range.getRange().upperEndpoint());
    }

    @Test
    @DisplayName("testIsStandardDate retourne true pour une date valide")
    void testIsStandardDateValid() {
        Assertions.assertTrue(LocalDateTimeRange.testIsStandardDate("2020-06-15 12:00:00"));
    }

    @Test
    @DisplayName("testIsStandardDate retourne false pour une chaîne invalide")
    void testIsStandardDateInvalid() {
        Assertions.assertFalse(LocalDateTimeRange.testIsStandardDate("not-a-date"));
        Assertions.assertFalse(LocalDateTimeRange.testIsStandardDate(""));
    }

    @Test
    @DisplayName("getLowerPointOrMin retourne lower quand la borne existe")
    void getLowerPointOrMinWithBound() {
        LocalDateTime lower = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTimeRange range = LocalDateTimeRange.since(lower);
        Assertions.assertEquals(lower, range.getLowerPointOrMin());
    }

    @Test
    @DisplayName("getLowerPointOrMin retourne LocalDateTime.MIN quand pas de borne inférieure")
    void getLowerPointOrMinWithoutBound() {
        LocalDateTimeRange range = LocalDateTimeRange.always();
        Assertions.assertEquals(LocalDateTime.MIN, range.getLowerPointOrMin());
    }

    @Test
    @DisplayName("getUpperEndpointOrMax retourne upper quand la borne existe")
    void getUpperEndpointOrMaxWithBound() {
        LocalDateTime upper = LocalDateTime.of(2022, 1, 1, 0, 0);
        LocalDateTimeRange range = LocalDateTimeRange.until(upper);
        Assertions.assertEquals(upper, range.getUpperEndpointOrMax());
    }

    @Test
    @DisplayName("getUpperEndpointOrMax retourne LocalDateTime.MAX quand pas de borne supérieure")
    void getUpperEndpointOrMaxWithoutBound() {
        LocalDateTimeRange range = LocalDateTimeRange.always();
        Assertions.assertEquals(LocalDateTime.MAX, range.getUpperEndpointOrMax());
    }

    @Test
    @DisplayName("parseSql(,(,)) retourne un range illimité")
    void parseSqlAll() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(,)");
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("parseSql atMost : (,\"2020-12-31 00:00:00\"]")
    void parseSqlAtMost() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(,\"2020-12-31 00:00:00\"]");
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertEquals(com.google.common.collect.BoundType.CLOSED, range.getRange().upperBoundType());
    }

    @Test
    @DisplayName("parseSql lessThan : (,\"2020-12-31 00:00:00\")")
    void parseSqlLessThan() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(,\"2020-12-31 00:00:00\")");
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertEquals(com.google.common.collect.BoundType.OPEN, range.getRange().upperBoundType());
    }

    @Test
    @DisplayName("parseSql atLeast : [\"2020-01-01 00:00:00\",)")
    void parseSqlAtLeast() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("[\"2020-01-01 00:00:00\",)");
        Assertions.assertTrue(range.getRange().hasLowerBound());
        Assertions.assertFalse(range.getRange().hasUpperBound());
        Assertions.assertEquals(com.google.common.collect.BoundType.CLOSED, range.getRange().lowerBoundType());
    }

    @Test
    @DisplayName("parseSql greaterThan : (\"2020-01-01 00:00:00\",)")
    void parseSqlGreaterThan() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(\"2020-01-01 00:00:00\",)");
        Assertions.assertTrue(range.getRange().hasLowerBound());
        Assertions.assertEquals(com.google.common.collect.BoundType.OPEN, range.getRange().lowerBoundType());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("parseSql openClosed : (\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\"]")
    void parseSqlOpenClosed() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\"]");
        Assertions.assertEquals(com.google.common.collect.BoundType.OPEN, range.getRange().lowerBoundType());
        Assertions.assertEquals(com.google.common.collect.BoundType.CLOSED, range.getRange().upperBoundType());
    }

    @Test
    @DisplayName("parseSql open : (\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\")")
    void parseSqlOpen() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("(\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\")");
        Assertions.assertEquals(com.google.common.collect.BoundType.OPEN, range.getRange().lowerBoundType());
        Assertions.assertEquals(com.google.common.collect.BoundType.OPEN, range.getRange().upperBoundType());
    }

    @Test
    @DisplayName("parseSql closed : [\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\"]")
    void parseSqlClosed() {
        LocalDateTimeRange range = LocalDateTimeRange.parseSql("[\"2020-01-01 00:00:00\",\"2021-01-01 00:00:00\"]");
        Assertions.assertEquals(com.google.common.collect.BoundType.CLOSED, range.getRange().lowerBoundType());
        Assertions.assertEquals(com.google.common.collect.BoundType.CLOSED, range.getRange().upperBoundType());
    }

    @Test
    @DisplayName("toSqlExpression pour range open (greaterThan) commence par (")
    void toSqlExpressionOpen() {
        LocalDateTimeRange range = LocalDateTimeRange.since(LocalDateTime.of(2020, 1, 1, 0, 0, 0));
        String sql = range.toSqlExpression();
        Assertions.assertTrue(sql.startsWith("["));
        Assertions.assertTrue(sql.endsWith(")"));
    }

    @Test
    @DisplayName("of(DatePattern, null, null) retourne always()")
    void ofWithNullBounds() {
        DatePattern<java.time.temporal.TemporalAccessor> pattern = DatePattern.of(DatePattern.DD_MM_YYYY);
        LocalDateTimeRange range = LocalDateTimeRange.of(pattern, (String) null, (String) null);
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("of(DatePattern, from, null) retourne always() car l'un des deux est null")
    void ofWithNullTo() {
        DatePattern<java.time.temporal.TemporalAccessor> pattern = DatePattern.of(DatePattern.DD_MM_YYYY);
        LocalDateTimeRange range = LocalDateTimeRange.of(pattern, "01/01/2020", (String) null);
        // si from||to == null, retourne always()
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("of(DatePattern, null, to) retourne always() car l'un des deux est null")
    void ofWithNullFrom() {
        DatePattern<java.time.temporal.TemporalAccessor> pattern = DatePattern.of(DatePattern.DD_MM_YYYY);
        LocalDateTimeRange range = LocalDateTimeRange.of(pattern, (String) null, "31/12/2020");
        // si from||to == null, retourne always()
        Assertions.assertFalse(range.getRange().hasLowerBound());
        Assertions.assertFalse(range.getRange().hasUpperBound());
    }

    @Test
    @DisplayName("parse(String, DateType) avec pattern MM/yyyy")
    void parseStringMonthYear() {
        DateType dateType = new DateType("MM/yyyy", null, null, null);
        LocalDateTimeRange range = LocalDateTimeRange.parse("06/2020", dateType);
        Assertions.assertEquals(LocalDateTime.of(2020, 6, 1, 0, 0, 0), range.getRange().lowerEndpoint());
    }

    @Test
    @DisplayName("parse(String, DateType) avec pattern dd/MM/yyyy")
    void parseStringDay() {
        DateType dateType = new DateType("dd/MM/yyyy", null, null, null);
        LocalDateTimeRange range = LocalDateTimeRange.parse("15/06/2020", dateType);
        Assertions.assertEquals(LocalDateTime.of(2020, 6, 15, 0, 0, 0), range.getRange().lowerEndpoint());
    }

    @Test
    @DisplayName("parse(String, DateType) avec pattern dd/MM/yyyy HH:mm:ss")
    void parseStringDateTime() {
        DateType dateType = new DateType("dd/MM/yyyy HH:mm:ss", null, null, null);
        LocalDateTimeRange range = LocalDateTimeRange.parse("15/06/2020 10:30:00", dateType);
        Assertions.assertEquals(LocalDateTime.of(2020, 6, 15, 0, 0, 0), range.getRange().lowerEndpoint());
    }

    @Test
    @DisplayName("parse(LocalDateTime, DateType) avec pattern MM/yyyy retourne null (converter non supporté)")
    void parseLocalDateTimeMonthYear() {
        // Le converter MM/yyyy retourne explicitement null pour LocalDateTime
        DateType dateType = new DateType("MM/yyyy", null, null, null);
        LocalDateTime dt = LocalDateTime.of(2021, 6, 15, 10, 30);
        LocalDateTimeRange result = LocalDateTimeRange.parse(dt, dateType);
        Assertions.assertNull(result);
    }

    @Test
    @DisplayName("parse(LocalDateTime, DateType) avec pattern dd/MM/yyyy")
    void parseLocalDateTimeDay() {
        DateType dateType = new DateType("dd/MM/yyyy", null, null, null);
        LocalDateTime dt = LocalDateTime.of(2021, 6, 15, 10, 30);
        LocalDateTimeRange range = LocalDateTimeRange.parse(dt, dateType);
        Assertions.assertEquals(LocalDateTime.of(2021, 6, 15, 0, 0, 0), range.getRange().lowerEndpoint());
    }

    @Test
    @DisplayName("parse(LocalDateTime, DateType) avec pattern dd/MM/yyyy HH:mm:ss")
    void parseLocalDateTimeDateTimePattern() {
        DateType dateType = new DateType("dd/MM/yyyy HH:mm:ss", null, null, null);
        LocalDateTime dt = LocalDateTime.of(2021, 6, 15, 10, 30);
        LocalDateTimeRange range = LocalDateTimeRange.parse(dt, dateType);
        Assertions.assertEquals(LocalDateTime.of(2021, 6, 15, 0, 0, 0), range.getRange().lowerEndpoint());
    }
}