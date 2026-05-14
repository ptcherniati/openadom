package fr.inra.oresing.domain.application.configuration.date;

import com.google.common.collect.*;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A vocation a représenter une donnée en base stockée sous forme de tsrange.
 * <p>
 * <a href="https://www.postgresql.org/docs/current/rangetypes.html">...</a>
 */
@Value
public class LocalDateTimeRange {
    public static final Set<String> ACCEPTED_START_OF_BOUNDS = Set.of("[", "(");
    public static final Set<String> ACCEPTED_END_OF_BOUNDS = Set.of("]", ")");
    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS).withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SQL_TIMESTAMP_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS);

    public static boolean testIsStandardDate(String dateString) {
        try {
            return LocalDateTimeRange.DATE_TIME_FORMATTER.format(LocalDateTimeRange.DATE_TIME_FORMATTER.parse(dateString)).equals(dateString);
        } catch (Exception e) {
            return false;
        }
    }

    private static final ImmutableSet<StringToLocalDateTimeRangeConverter> ALL_CONVERTERS = ImmutableSet.of(
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "MM/yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final String str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    final String pattern = "01/" + str;
                    LocalDate date = LocalDate.parse(pattern, DATE_FORMATTER_DDMMYYYY);
                    if (dateType != null && dateType.duration != null) {
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return between(LocalDate.from(date.atStartOfDay()), date.plusMonths(1L));
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final LocalDateTime str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    return null;
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final String str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    if (dateType != null && dateType.duration != null) {
                        final String pattern = "01/01/" + str;
                        LocalDate date = LocalDate.parse(pattern, DATE_FORMATTER_DDMMYYYY);
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return forYear(Year.parse(str, dateTimeFormatter));
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final LocalDateTime str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    if (dateType != null && dateType.duration != null) {
                        final String pattern = "01/01/" + str;
                        LocalDate date = LocalDate.parse(pattern, DATE_FORMATTER_DDMMYYYY);
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return forYear(Year.of(str.getYear()));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final String str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    if (dateType != null && dateType.duration != null) {
                        LocalDate date = LocalDate.parse(str, DATE_FORMATTER_DDMMYYYY);
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return forDay(LocalDate.parse(str, dateTimeFormatter));
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final LocalDateTime str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    if (dateType != null && dateType.duration != null) {
                        LocalDate date = LocalDate.from(str);
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return forDay(LocalDate.from(str));
                }
            },
            new StringToLocalDateTimeRangeConverter() {
                @Override
                public String getPattern() {
                    return "dd/MM/yyyy HH:mm:ss";
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final String str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    LocalDate startDate = LocalDate.parse(str, dateTimeFormatter);
                    if (dateType != null && dateType.duration != null) {
                        LocalDateTime date = LocalDateTime.parse(str, DateTimeFormatter.ofPattern(getPattern()));
                        return new Duration(dateType.duration).getLocalDateTimeRange(date);
                    }
                    return forDay(startDate);
                }

                @Override
                public LocalDateTimeRange toLocalDateTimeRange(final LocalDateTime str, final DateTimeFormatter dateTimeFormatter, final DateType dateType) {
                    LocalDate startDate = LocalDate.from(str);
                    if (dateType != null && dateType.duration != null) {
                        return new Duration(dateType.duration).getLocalDateTimeRange(str);
                    }
                    return forDay(startDate);
                }
            }
    );
    private static final ImmutableMap<String, StringToLocalDateTimeRangeConverter> CONVERTER_PER_PATTERNS =
            Maps.uniqueIndex(ALL_CONVERTERS, StringToLocalDateTimeRangeConverter::getPattern);
    public static final ImmutableSet<String> KNOWN_PATTERNS = CONVERTER_PER_PATTERNS.keySet();
    Range<LocalDateTime> range;

    public LocalDateTimeRange(final List<LocalDateTime> dates) {
        super();
        range = between(dates.get(0), dates.get(1)).range;
    }

    public LocalDateTimeRange(final Range<LocalDateTime> range) {
        super();
        this.range = range;
    }

    public static SiOreIllegalArgumentException getError(final String boundValue, final String lowerBound, final String upperBound, final Set<String> acceptedValues) {
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

    public static SiOreIllegalArgumentException getErrorBoundType(final BoundType boundType) {
        return new SiOreIllegalArgumentException(
                "badBoundTypeForInterval",
                Map.of(
                        "boundType", boundType,
                        "knownBoundType", Arrays.stream(BoundType.values()).map(BoundType::toString).collect(Collectors.toSet())
                )
        );
    }

    public static LocalDateTimeRange always() {
        return new LocalDateTimeRange(Range.all());
    }

    public static LocalDateTimeRange forYear(final Year year) {
        final LocalDate fromDay = year.atMonthDay(MonthDay.of(Month.JANUARY, 1));
        final LocalDate toDay = year.plusYears(1).atMonthDay(MonthDay.of(Month.JANUARY, 1));
        return between(fromDay, toDay);
    }

    public static LocalDateTimeRange forDay(final LocalDate localDate) {
        return between(localDate, localDate.plusDays(1));
    }

    public static LocalDateTimeRange between(final LocalDate fromDay, final LocalDate toDay) {
        final LocalDateTime lowerBound = fromDay.atTime(0, 0, 0);
        final LocalDateTime upperBound = toDay.atTime(0, 0, 0);
        return between(lowerBound, upperBound);
    }

    public static LocalDateTimeRange since(final LocalDate since) {
        return since(since.atTime(0, 0, 0));
    }

    public static LocalDateTimeRange until(final LocalDate until) {
        return until(until.atTime(0, 0, 0));
    }

    public static LocalDateTimeRange between(final LocalDateTime lowerBound, final LocalDateTime upperBound) {
        return new LocalDateTimeRange(Range.closedOpen(lowerBound, upperBound));
    }

    public static LocalDateTimeRange since(final LocalDateTime since) {
        return new LocalDateTimeRange(Range.atLeast(since));
    }

    public static LocalDateTimeRange until(final LocalDateTime until) {
        return new LocalDateTimeRange(Range.lessThan(until));
    }

    public static LocalDateTimeRange parseSql(final String sqlExpression) {
        final String[] split = StringUtils.split(sqlExpression, ",");
        final String lowerBoundString = split[0];
        final String upperBoundString = split[1];
        final Range<LocalDateTime> range;
        if ("(".equals(lowerBoundString)) {
            range = parseLowerBound(upperBoundString);
        } else {
            range = parseUpperBound(lowerBoundString, upperBoundString);
        }
        return new LocalDateTimeRange(range);
    }

    private static Range<LocalDateTime> parseUpperBound(final String lowerBoundString, final String upperBoundString) {
        final Range<LocalDateTime> range;
        final LocalDateTime lowerBound = parseBound(lowerBoundString);
        if (")".equals(upperBoundString)) {
            if (lowerBoundString.startsWith("[")) {
                range = Range.atLeast(lowerBound);
            } else if (lowerBoundString.startsWith("(")) {
                range = Range.greaterThan(lowerBound);
            } else {
                throw getError(lowerBoundString.substring(0, 1), lowerBoundString, upperBoundString, ACCEPTED_START_OF_BOUNDS);

            }
        } else {
            final LocalDateTime upperBound = parseBound(upperBoundString);
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

    private static Range<LocalDateTime> parseLowerBound(final String lowerBoundString) {
        final Range<LocalDateTime> range;
        if (")".equals(lowerBoundString)) {
            range = Range.all();
        } else {
            final LocalDateTime upperBound = parseBound(lowerBoundString);
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

    public static LocalDateTimeRange parse(final LocalDateTime value, final DateType dateType) {
        return Objects.requireNonNull(CONVERTER_PER_PATTERNS.get(dateType.pattern)).toLocalDateTimeRange(value, dateType);
    }

    public static LocalDateTimeRange parse(final String value, final DateType dateType) {
        return Objects.requireNonNull(CONVERTER_PER_PATTERNS.get(dateType.pattern)).toLocalDateTimeRange(value, dateType);
    }

    private static LocalDateTime parseBound(final String boundString) {
        final String stripped = StringUtils.strip(boundString, "()[]\"");
        return LocalDateTime.parse(stripped, SQL_TIMESTAMP_DATE_TIME_FORMATTER);
    }

    private static String formatBound(final LocalDateTime bound) {
        return "\"" + SQL_TIMESTAMP_DATE_TIME_FORMATTER.format(bound) + "\"";
    }

    public static LocalDateTimeRange of(DatePattern<?> datePattern, TemporalAccessor from, TemporalAccessor to) {
        return switch (datePattern.typeOfDate()) {
            case DATE -> LocalDateTimeRange.between(((LocalDate) from).atStartOfDay(), ((LocalDate) to).atStartOfDay());
            case DATETIME -> LocalDateTimeRange.between(((LocalDateTime) from), ((LocalDateTime) to));
            case TIME ->
                    LocalDateTimeRange.between(((LocalTime) from).atDate(LocalDate.EPOCH), ((LocalTime) to).atDate(LocalDate.EPOCH));
        };
    }

    public static LocalDateTimeRange of(DatePattern<?> datePattern, String from, String to) {
        if (from == null || to == null) {
            return LocalDateTimeRange.always();
        }
        from = datePattern.dateFromStandardFormat(from);
        TemporalAccessor fromTemporal = datePattern.format(from);
        to = datePattern.dateFromStandardFormat(to);
        TemporalAccessor totemporal = datePattern.format(to, true);
        if (fromTemporal == null) {
            return switch (datePattern.typeOfDate()) {
                case DATETIME -> LocalDateTimeRange.until((LocalDateTime) totemporal);
                case DATE -> LocalDateTimeRange.until((LocalDate) totemporal);
                case TIME -> LocalDateTimeRange.until(((LocalTime) totemporal).atDate(LocalDate.EPOCH));
            };
        }
        if (totemporal == null) {
            return switch (datePattern.typeOfDate()) {
                case DATETIME -> LocalDateTimeRange.since((LocalDateTime) fromTemporal);
                case DATE -> LocalDateTimeRange.since((LocalDate) fromTemporal);
                case TIME -> LocalDateTimeRange.since(((LocalTime) fromTemporal).atDate(LocalDate.EPOCH));
            };
        }
        return of(datePattern, fromTemporal, totemporal);
    }

    public String toSqlExpression() {
        final Range<LocalDateTime> currentRange = this.range;
        final String lowerBoundString;
        if (currentRange.hasLowerBound()) {
            final LocalDateTime bound = currentRange.lowerEndpoint();
            final String formattedLowerBound = formatBound(bound);
            if (currentRange.lowerBoundType() == BoundType.OPEN) {
                lowerBoundString = "(" + formattedLowerBound;
            } else if (currentRange.lowerBoundType() == BoundType.CLOSED) {
                lowerBoundString = "[" + formattedLowerBound;
            } else {
                throw getErrorBoundType(currentRange.lowerBoundType());
            }
        } else {
            lowerBoundString = "(";
        }
        final String upperBoundString;
        if (currentRange.hasUpperBound()) {
            final String formattedUpperBound = formatBound(currentRange.upperEndpoint());
            if (currentRange.upperBoundType() == BoundType.OPEN) {
                upperBoundString = formattedUpperBound + ")";
            } else if (currentRange.upperBoundType() == BoundType.CLOSED) {
                upperBoundString = formattedUpperBound + "]";
            } else {
                throw getErrorBoundType(currentRange.upperBoundType());
            }
        } else {
            upperBoundString = ")";
        }
        return lowerBoundString + "," + upperBoundString;
    }

    interface StringToLocalDateTimeRangeConverter {

        String getPattern();

        default LocalDateTimeRange toLocalDateTimeRange(final String str, final DateType dateType) {
            return toLocalDateTimeRange(str, DateTimeFormatter.ofPattern(getPattern()), dateType);
        }

        default LocalDateTimeRange toLocalDateTimeRange(final LocalDateTime str, final DateType dateType) {
            return toLocalDateTimeRange(str, DateTimeFormatter.ofPattern(getPattern()), dateType);
        }

        LocalDateTimeRange toLocalDateTimeRange(String str, DateTimeFormatter dateTimeFormatter, DateType dateType);

        LocalDateTimeRange toLocalDateTimeRange(LocalDateTime str, DateTimeFormatter dateTimeFormatter, DateType dateType);
    }

    public LocalDateTime getLowerPointOrMin() {
        return getRange().hasLowerBound() ? getRange().lowerEndpoint() : LocalDateTime.MIN;
    }

    public LocalDateTime getUpperEndpointOrMax() {
        return getRange().hasUpperBound() ? getRange().upperEndpoint() : LocalDateTime.MAX;
    }

}