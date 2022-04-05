package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;
import org.apache.commons.lang3.StringUtils;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

public class DateLineChecker implements CheckerOnOneVariableComponentLineChecker<DateLineCheckerConfiguration> {

    public static final String PATTERN_DATE_REGEXP = "^date:.{19}:";
    private final CheckerTarget target;
    private final DateLineCheckerConfiguration configuration;
    @JsonIgnore
    private final LineTransformer transformer;

    public static boolean isValidPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return false;
        }
        try {
            newDateTimeFormatter(pattern);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static DateTimeFormatter newDateTimeFormatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern);
    }

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
        this.dateTimeFormatter = newDateTimeFormatter(pattern);
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
            validationCheckResult = DateValidationCheckResult.success(target, date);
        } catch (DateTimeParseException e) {
            validationCheckResult = DateValidationCheckResult.error(
                    target,
                    getTarget().getInternationalizedKey("invalidDate"), ImmutableMap.of(
                            "target", target,
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