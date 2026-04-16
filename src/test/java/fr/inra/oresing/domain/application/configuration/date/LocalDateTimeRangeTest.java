package fr.inra.oresing.domain.application.configuration.date;

import com.google.common.collect.BoundType;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

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
}