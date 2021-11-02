package fr.inra.oresing.model;

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * A vocation a représenter une donnée en base stockée sous forme de tsrange.
 *
 * https://www.postgresql.org/docs/current/rangetypes.html
 */
@Value
public class LocalDateTimeRange {

    Range<LocalDateTime> range;

    private static final DateTimeFormatter SQL_TIMESTAMP_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ImmutableSet<StringToLocalDateTimeRangeConverter> ALL_CONVERTERS = ImmutableSet.of(
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter) {
                    return LocalDateTimeRange.forYear(Year.parse(str, dateTimeFormatter));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter) {
                    return LocalDateTimeRange.forDay(LocalDate.parse(str, dateTimeFormatter));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy HH:mm:ss";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter) {
                    return LocalDateTimeRange.forDay(LocalDate.parse(str, dateTimeFormatter));
                }
            }
    );

    private static final ImmutableMap<String, StringToLocalDateTimeRangeConverter> CONVERTER_PER_PATTERNS =
            Maps.uniqueIndex(ALL_CONVERTERS, StringToLocalDateTimeRangeConverter::getPattern);

    public static final ImmutableSet<String> KNOWN_PATTERNS = CONVERTER_PER_PATTERNS.keySet();

    public static LocalDateTimeRange always() {
        return new LocalDateTimeRange(Range.all());
    }

    public static LocalDateTimeRange forYear(Year year) {
        LocalDate fromDay = year.atMonthDay(MonthDay.of(Month.JANUARY, 1));
        LocalDate toDay = year.plusYears(1).atMonthDay(MonthDay.of(Month.JANUARY, 1));
        return between(fromDay, toDay);
    }

    public static LocalDateTimeRange forDay(LocalDate localDate) {
        return between(localDate, localDate.plusDays(1));
    }

    public static LocalDateTimeRange between(LocalDate fromDay, LocalDate toDay) {
        LocalDateTime lowerBound = fromDay.atTime(0, 0, 0);
        LocalDateTime upperBound = toDay.atTime(0, 0, 0);
        return between(lowerBound, upperBound);
    }

    public static LocalDateTimeRange since(LocalDate since) {
        return since(since.atTime(0, 0, 0));
    }

    public static LocalDateTimeRange until(LocalDate until) {
        return until(until.atTime(0, 0, 0));
    }

    public static LocalDateTimeRange between(LocalDateTime lowerBound, LocalDateTime upperBound) {
        return new LocalDateTimeRange(Range.closedOpen(lowerBound, upperBound));
    }

    public static LocalDateTimeRange since(LocalDateTime since) {
        return new LocalDateTimeRange(Range.atLeast(since));
    }

    public static LocalDateTimeRange until(LocalDateTime until) {
        return new LocalDateTimeRange(Range.lessThan(until));
    }

    public static LocalDateTimeRange parseSql(String sqlExpression) {
        String[] split = StringUtils.split(sqlExpression, ",");
        String lowerBoundString = split[0];
        String upperBoundString = split[1];
        Range<LocalDateTime> range;
        if (lowerBoundString.equals("(")) {
            range = parseLowerBound(upperBoundString);
        } else {
            range = parseUpperBound(lowerBoundString, upperBoundString);
        }
        return new LocalDateTimeRange(range);
    }

    private static Range<LocalDateTime> parseUpperBound(String lowerBoundString, String upperBoundString) {
        Range<LocalDateTime> range;
        LocalDateTime lowerBound = parseBound(lowerBoundString);
        if (upperBoundString.equals(")")) {
            if (lowerBoundString.startsWith("[")) {
                range = Range.atLeast(lowerBound);
            } else if (lowerBoundString.startsWith("(")) {
                range = Range.greaterThan(lowerBound);
            } else {
                throw new IllegalStateException(upperBoundString);
            }
        } else {
            LocalDateTime upperBound = parseBound(upperBoundString);
            if (lowerBoundString.startsWith("[")) {
                if (upperBoundString.endsWith("]")) {
                    range = Range.closed(lowerBound, upperBound);
                } else if (upperBoundString.endsWith(")")) {
                    range = Range.closedOpen(lowerBound, upperBound);
                } else {
                    throw new IllegalStateException(upperBoundString);
                }
            } else if (lowerBoundString.startsWith("(")) {
                if (upperBoundString.endsWith("]")) {
                    range = Range.openClosed(lowerBound, upperBound);
                } else if (upperBoundString.endsWith(")")) {
                    range = Range.open(lowerBound, upperBound);
                } else {
                    throw new IllegalStateException(upperBoundString);
                }
            } else {
                throw new IllegalStateException(lowerBoundString);
            }
        }
        return range;
    }

    private static Range<LocalDateTime> parseLowerBound(String upperBoundString) {
        Range<LocalDateTime> range;
        if (upperBoundString.equals(")")) {
            range = Range.all();
        } else {
            LocalDateTime upperBound = parseBound(upperBoundString);
            if (upperBoundString.endsWith("]")) {
                range = Range.atMost(upperBound);
            } else if (upperBoundString.endsWith(")")) {
                range = Range.lessThan(upperBound);
            } else {
                throw new IllegalStateException(upperBoundString);
            }
        }
        return range;
    }

    public static ImmutableSet<String> getKnownPatterns() {
        return KNOWN_PATTERNS;
    }

    public static LocalDateTimeRange parse(String value, String pattern) {
        return CONVERTER_PER_PATTERNS.get(pattern).toLocalDateTimeRange(value);
    }

    public String toSqlExpression() {
        Range<LocalDateTime> range = getRange();
        String lowerBoundString;
        if (range.hasLowerBound()) {
            LocalDateTime bound = range.lowerEndpoint();
            String formattedLowerBound = formatBound(bound);
            if (range.lowerBoundType() == BoundType.OPEN) {
                lowerBoundString = "(" + formattedLowerBound;
            } else if (range.lowerBoundType() == BoundType.CLOSED) {
                lowerBoundString = "[" + formattedLowerBound;
            } else {
                throw new IllegalStateException(range + " borné par " + range.lowerBoundType());
            }
        } else {
            lowerBoundString = "(";
        }
        String upperBoundString;
        if (range.hasUpperBound()) {
            String formattedUpperBound = formatBound(range.upperEndpoint());
            if (range.upperBoundType() == BoundType.OPEN) {
                upperBoundString = formattedUpperBound + ")";
            } else if (range.upperBoundType() == BoundType.CLOSED) {
                upperBoundString = formattedUpperBound + "]";
            } else {
                throw new IllegalStateException(range + " borné par " + range.upperBoundType());
            }
        } else {
            upperBoundString = ")";
        }
        String sqlExpression = lowerBoundString + "," + upperBoundString;
        return sqlExpression;
    }

    private static LocalDateTime parseBound(String boundString) {
        String stripped = StringUtils.strip(boundString, "()[]\"");
        return LocalDateTime.parse(stripped, SQL_TIMESTAMP_DATE_TIME_FORMATTER);
    }

    private static String formatBound(LocalDateTime bound) {
        return "\"" + SQL_TIMESTAMP_DATE_TIME_FORMATTER.format(bound) + "\"";
    }

    interface StringToLocalDateTimeRangeConverter {

        String getPattern();

        default LocalDateTimeRange toLocalDateTimeRange(String str) {
            return toLocalDateTimeRange(str, DateTimeFormatter.ofPattern(getPattern()));
        }

        LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter);
    }
}