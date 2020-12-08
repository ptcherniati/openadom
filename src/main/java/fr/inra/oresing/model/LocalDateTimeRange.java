package fr.inra.oresing.model;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

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

    public static LocalDateTimeRange always() {
        return new LocalDateTimeRange(Range.all());
    }

    public static LocalDateTimeRange forYear(Year year) {
        LocalDateTime lowerBound = year.atMonthDay(MonthDay.of(Month.JANUARY, 1)).atTime(0, 0, 0);
        LocalDateTime upperBound = year.atMonthDay(MonthDay.of(Month.DECEMBER, 31)).atTime(0, 0, 0);
        return new LocalDateTimeRange(Range.closedOpen(lowerBound, upperBound));
    }

    public static LocalDateTimeRange parseSql(String sqlExpression) {
        String[] split = StringUtils.split(sqlExpression, ",");
        String lowerBoundString = split[0];
        String upperBoundString = split[1];
        Range<LocalDateTime> range;
        if (lowerBoundString.equals("(")) {
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
        } else {
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
        }
        return new LocalDateTimeRange(range);
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
}
