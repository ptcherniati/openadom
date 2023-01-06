package fr.inra.oresing.model;

import com.google.common.collect.*;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A vocation a représenter une donnée en base stockée sous forme de tsrange.
 * <p>
 * https://www.postgresql.org/docs/current/rangetypes.html
 */
@Value
public class LocalDateTimeRange {

    public static LocalDateTimeRange getTimeScope(LocalDate fromDay, LocalDate toDay) {
        LocalDateTimeRange timeScope;
        if (fromDay == null) {
            if (toDay == null) {
                timeScope = LocalDateTimeRange.always();
            } else {
                timeScope = LocalDateTimeRange.until(toDay);
            }
        } else {
            if (toDay == null) {
                timeScope = LocalDateTimeRange.since(fromDay);
            } else {
                timeScope = LocalDateTimeRange.between(fromDay, toDay);
            }
        }
        return timeScope;
    }

    public static final Set<String> ACCEPTED_START_OF_BOUNDS = Set.of("[", "(");
    public static final Set<String> ACCEPTED_END_OF_BOUNDS = Set.of("]", ")");

    public static SiOreIllegalArgumentException getError(String boundValue, String lowerBound, String upperBound, Set<String> acceptedValues) {
        return new SiOreIllegalArgumentException(
                "badBoundsForInterval",
                Map.of(
                        "boundValue", boundValue,
                        "lowerBound", lowerBound,
                        "upperBound", upperBound,
                        "acceptedValues", acceptedValues
                )
        );
    }

    public static SiOreIllegalArgumentException getErrorBoundType(BoundType boundType) {
        return new SiOreIllegalArgumentException(
                "badBoundTypeForInterval",
                Map.of(
                        "boundType", boundType,
                        "knownBoundType", Arrays.stream(BoundType.values()).map(BoundType::toString).collect(Collectors.toSet())
                )
        );
    }

    private static final DateTimeFormatter SQL_TIMESTAMP_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ImmutableSet<StringToLocalDateTimeRangeConverter> ALL_CONVERTERS = ImmutableSet.of(
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "MM/yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateLineChecker dateLineChecker) {
                    String pattern = "01/" + str;
                    final LocalDate date = LocalDate.parse(pattern, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    if (dateLineChecker.getConfiguration() != null && dateLineChecker.getConfiguration().getDuration() != null) {
                        return new Duration(dateLineChecker.getConfiguration().getDuration()).getLocalDateTimeRange(date);
                    }
                    return LocalDateTimeRange.between(LocalDate.from(date.atStartOfDay()), date.plus(1L, ChronoUnit.MONTHS));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateLineChecker dateLineChecker) {
                    if (dateLineChecker.getConfiguration() != null && dateLineChecker.getConfiguration().getDuration() != null) {
                        String pattern = "01/01/" + str;
                        final LocalDate date = LocalDate.parse(pattern, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        return new Duration(dateLineChecker.getConfiguration().getDuration()).getLocalDateTimeRange(date);
                    }
                    return LocalDateTimeRange.forYear(Year.parse(str, dateTimeFormatter));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateLineChecker dateLineChecker) {
                    if (dateLineChecker.getConfiguration() != null && dateLineChecker.getConfiguration().getDuration() != null) {
                        final LocalDate date = LocalDate.parse(str, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        return new Duration(dateLineChecker.getConfiguration().getDuration()).getLocalDateTimeRange(date);
                    }
                    return LocalDateTimeRange.forDay(LocalDate.parse(str, dateTimeFormatter));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy HH:mm:ss";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateLineChecker dateLineChecker) {
                    final LocalDate startDate = LocalDate.parse(str, dateTimeFormatter);
                    if (dateLineChecker.getConfiguration() != null && dateLineChecker.getConfiguration().getDuration() != null) {
                        final LocalDateTime date = LocalDateTime.parse(str, DateTimeFormatter.ofPattern(getPattern()));
                        return new Duration(dateLineChecker.getConfiguration().getDuration()).getLocalDateTimeRange(date);
                    }
                    return LocalDateTimeRange.forDay(startDate);
                }
            }
    );
    private static final ImmutableMap<String, StringToLocalDateTimeRangeConverter> CONVERTER_PER_PATTERNS =
            Maps.uniqueIndex(ALL_CONVERTERS, StringToLocalDateTimeRangeConverter::getPattern);
    public static final ImmutableSet<String> KNOWN_PATTERNS = CONVERTER_PER_PATTERNS.keySet();
    Range<LocalDateTime> range;

    public LocalDateTimeRange(List<LocalDateTime> dates) {
        this.range = LocalDateTimeRange.between(dates.get(0), dates.get(1)).getRange();
    }

    public LocalDateTimeRange(Range<LocalDateTime> range) {
        this.range = range;
    }

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
                throw getError(lowerBoundString.substring(0, 1), lowerBoundString, upperBoundString, ACCEPTED_START_OF_BOUNDS);

            }
        } else {
            LocalDateTime upperBound = parseBound(upperBoundString);
            if (lowerBoundString.startsWith("[")) {
                if (upperBoundString.endsWith("]")) {
                    range = Range.closed(lowerBound, upperBound);
                } else if (upperBoundString.endsWith(")")) {
                    range = Range.closedOpen(lowerBound, upperBound);
                } else {
                    throw getError(upperBoundString.substring(upperBoundString.length() - 1), lowerBoundString, upperBoundString, ACCEPTED_END_OF_BOUNDS);
                }
            } else if (lowerBoundString.startsWith("(")) {
                if (upperBoundString.endsWith("]")) {
                    range = Range.openClosed(lowerBound, upperBound);
                } else if (upperBoundString.endsWith(")")) {
                    range = Range.open(lowerBound, upperBound);
                } else {
                    throw getError(upperBoundString.substring(upperBoundString.length() - 1), lowerBoundString, null, ACCEPTED_END_OF_BOUNDS);
                }
            } else {
                throw getError(lowerBoundString.substring(0, 1), lowerBoundString, upperBoundString, ACCEPTED_START_OF_BOUNDS);
            }
        }
        return range;
    }

    private static Range<LocalDateTime> parseLowerBound(String lowerBoundString) {
        Range<LocalDateTime> range;
        if (lowerBoundString.equals(")")) {
            range = Range.all();
        } else {
            LocalDateTime upperBound = parseBound(lowerBoundString);
            if (lowerBoundString.endsWith("]")) {
                range = Range.atMost(upperBound);
            } else if (lowerBoundString.endsWith(")")) {
                range = Range.lessThan(upperBound);
            } else {
                throw getError(lowerBoundString.substring(lowerBoundString.length() - 1), lowerBoundString, null, ACCEPTED_END_OF_BOUNDS);
            }
        }
        return range;
    }

    public static ImmutableSet<String> getKnownPatterns() {
        return KNOWN_PATTERNS;
    }

    public static LocalDateTimeRange parse(String value, DateLineChecker pattern) {
        return CONVERTER_PER_PATTERNS.get(pattern.getPattern()).toLocalDateTimeRange(value, pattern);
    }

    private static LocalDateTime parseBound(String boundString) {
        String stripped = StringUtils.strip(boundString, "()[]\"");
        return LocalDateTime.parse(stripped, SQL_TIMESTAMP_DATE_TIME_FORMATTER);
    }

    private static String formatBound(LocalDateTime bound) {
        return "\"" + SQL_TIMESTAMP_DATE_TIME_FORMATTER.format(bound) + "\"";
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
                throw getErrorBoundType(range.lowerBoundType());
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
                throw getErrorBoundType(range.upperBoundType());
            }
        } else {
            upperBoundString = ")";
        }
        String sqlExpression = lowerBoundString + "," + upperBoundString;
        return sqlExpression;
    }

    interface StringToLocalDateTimeRangeConverter {

        String getPattern();

        default LocalDateTimeRange toLocalDateTimeRange(String str, DateLineChecker dateLineChecker) {
            return toLocalDateTimeRange(str, DateTimeFormatter.ofPattern(getPattern()), dateLineChecker);
        }

        LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateLineChecker dateLineChecker);
    }

}