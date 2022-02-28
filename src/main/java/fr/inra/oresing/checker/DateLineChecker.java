package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

public class DateLineChecker implements CheckerOnOneVariableComponentLineChecker<DateLineCheckerConfiguration> {

    public static final String PARAM_PATTERN = "pattern";
    public static final String PARAM_DATE_TIME_FORMATTER = "dateTimeFormatter";
    public static final String PARAM_DATE = "date";
    public static final String PATTERN_DATE_REGEXP = "^date:.{19}:";
    private final CheckerTarget target;
    private final DateLineCheckerConfiguration configuration;
    @JsonIgnore
    private final LineTransformer transformer;

    public CheckerTarget getTarget(){
        return this.target;
    }

    private final DateTimeFormatter dateTimeFormatter;

    private final String pattern;
    public static String sortableDateToFormattedDate(String formattedDate){
        return formattedDate.replaceAll(PATTERN_DATE_REGEXP, "");
    }

    public DateLineChecker(CheckerTarget target, String pattern, DateLineCheckerConfiguration configuration, LineTransformer transformer) {
        this.configuration = configuration;
        this.target = target;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        this.pattern = pattern;
        this.transformer = transformer;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            value = sortableDateToFormattedDate(value);
            TemporalAccessor date = dateTimeFormatter.parse(value);
            Map<String, Object> params = ImmutableMap.of(
                    PARAM_PATTERN, pattern,
                    PARAM_DATE_TIME_FORMATTER, dateTimeFormatter,
                    target.getType().name(), target.getTarget(),
                    PARAM_DATE, date
            );
            validationCheckResult = DateValidationCheckResult.success(params);
        } catch (DateTimeParseException e) {
            validationCheckResult = DateValidationCheckResult.error(
                    getTarget().getInternationalizedKey("invalidDate"), ImmutableMap.of(
                            "target", target.getTarget(),
                            "pattern", pattern,
                            "value", value));
        }
        return validationCheckResult;
    }

    @Override
    public DateLineCheckerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public LineTransformer getTransformer() {
        return transformer;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.COMPOSITE_DATE;
    }
}