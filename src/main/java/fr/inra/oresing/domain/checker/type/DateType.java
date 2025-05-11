package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultCheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public non-sealed class DateType implements FieldType<LocalDateTime> {

    public static final String PATTERN_DATE_REGEXP = "^date:.{19}:";
    public static final String PATTERN_DATE_REGEXP_FIND_DATE = "^date:(.{19}):(.*)";
    public final String pattern;
    @JsonIgnore
    public final DateTimeFormatter formatter;
    public String duration;
    public String sortableDate;
    public TemporalAccessor minDate;
    public TemporalAccessor maxDate;
    public static final String LOWER_THAN_MIN = "LOWER_THAN_MIN";
    public static final String HIGHER_THAN_MAX = "HIGHER_THAN_MAX";

    Supplier<DateType> clone;


    public DateType(final String pattern, final String duration, final TemporalAccessor minDate, final TemporalAccessor maxDate) {
        super();
        this.pattern = Strings.isNullOrEmpty(pattern)?"dd/MM/yyyy":pattern;
        this.formatter = new DateTimeFormatterBuilder().appendPattern(this.pattern).toFormatter();
        this.duration = duration;
        this.minDate = minDate;
        this.maxDate = maxDate;
    }

    public DateType(final String pattern, final LocalDateTime value, final Supplier<DateType> clone) {
        super();
        this.pattern = Strings.isNullOrEmpty(pattern)?"dd/MM/yyyy":pattern;
        this.value = value;
        formatter = new DateTimeFormatterBuilder().appendPattern(this.pattern).toFormatter();
        this.clone = clone;
    }

    public DateType() {
        super();
        pattern = "dd/MM/yyyy";
        formatter = DateTimeFormatter.ofPattern(pattern);
    }

    private static DateTimeFormatter newDateTimeFormatter(final String pattern) {
        return DateTimeFormatter.ofPattern(pattern);
    }
    public static String sorteableDateToFormattedDate(final String dateString){
        final Matcher m = Pattern.compile(PATTERN_DATE_REGEXP_FIND_DATE).matcher(dateString);
        if(m.matches()) {
            return DateTimeFormatter.ofPattern(m.group(2)).format(DateTimeFormatter.ISO_DATE_TIME.parse(m.group(1)));
        }
        return dateString;
    }

    public static DateType of(String date) {
        final Matcher matcher = Pattern.compile(PATTERN_DATE_REGEXP_FIND_DATE).matcher(date);
        if(matcher.matches()){
            final Supplier<DateType>  clone= ()-> of(date);
            final String dateString = matcher.group(1);
            final String pattern = matcher.group(2);
            final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(pattern);
            return new DateType(pattern, LocalDateTime.parse(dateString, dateFormatter), clone);
        }
        return new DateType();
    }

    @Override
    public String toString() {
        return Optional.ofNullable(value).map(this::toComparableDate).orElse(null);
    }

    public String toComparableDate(final LocalDateTime date) {
        return Optional.ofNullable(date)
                .map(localDateTime -> localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .map(s -> String.format("date:%s:%s", s,pattern))
                .orElse("");
    }

    public String toComparableDate(final String dateString) {
        if(Pattern.compile(PATTERN_DATE_REGEXP_FIND_DATE).matcher(dateString).matches()){
            return dateString;
        }
        final LocalDateTime date = valueToDate(formatter, dateString);
        return String.format("date:%s:%s", date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), pattern);
    }

    public static String sortableDateToFormattedDate(final String formattedDate) {
        return formattedDate.replaceAll(PATTERN_DATE_REGEXP, "");
    }

    LocalDateTime value;

    @Override
    public LocalDateTime getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        CheckerValidationCheckResult validationCheckResult;
        final DataColumn target = lineChecker.target();
        final Matcher matcher = Pattern.compile(PATTERN_DATE_REGEXP_FIND_DATE).matcher(value);
        LocalDateTime valuetoDate= null;
        if(matcher.matches()){
            final String group = matcher.group(1);
            valuetoDate = LocalDateTime.parse(group, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        try {
            this.value = valuetoDate!=null?valuetoDate:valueToDate(formatter, value);
            sortableDate = toComparableDate(value);
            if (minDate != null && this.value.compareTo((ChronoLocalDateTime<?>) minDate) < 0) {
                throw new IllegalArgumentException(LOWER_THAN_MIN);
            }
            if (maxDate != null && this.value.compareTo((ChronoLocalDateTime<?>) maxDate) > 0) {
                throw new IllegalArgumentException(HIGHER_THAN_MAX);
            }
            validationCheckResult = DateValidationCheckResult.success(target, List.of(this.value), this);
        } catch (final DateTimeParseException e) {
            validationCheckResult = DateValidationCheckResult.error(
                    target,
                    target.getInternationalizedKey("invalidDate"),
                    ImmutableMap.of(
                            "component", target.column(),
                            "pattern", pattern,
                            "value", value)
            ,null);
        } catch (final IllegalArgumentException e) {
            validationCheckResult = DefaultCheckerValidationCheckResult.error(
                    target.getInternationalizedKey("badIntervalDate"), ImmutableMap.of(
                            "component", target.column(),
                            "value", value,
                            "type", e.getMessage(),
                            "bound", LOWER_THAN_MIN.equals(e.getMessage()) ? minDate : maxDate
                    ),
                    target
            );
        }
        return validationCheckResult;
    }

    public static LocalDateTime valueToDate(final DateTimeFormatter formatter, String value) {
        String value1 = sortableDateToFormattedDate(value.trim());
        final TemporalAccessor dateParsed = formatter.parse(value1);
        LocalDate localdate = dateParsed.query(TemporalQueries.localDate());
        localdate = localdate == null ? LocalDate.of(1970, 1, 1) : localdate;
        LocalTime localTime = dateParsed.query(TemporalQueries.localTime());
        localTime = localTime == null ? LocalTime.MIN : localTime;
        return localdate.atTime(localTime);
    }

    @Override
    public FieldType toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final DateType dateType = new DateType(
                pattern,
                duration,
                minDate,
                maxDate
        );
        dateType.value = value;
        return dateType;
    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if(value==null){
            gen.writeNull();
        }
        gen.writeString(toComparableDate(value));
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        gen.writeObjectField(key, toComparableDate(value));
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
                node.put(key, toComparableDate(value));
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        arrayNode.add(toComparableDate(value));

    }

    @Override
    public Object toJsonForFrontend() {
        return sortableDate;
    }
}