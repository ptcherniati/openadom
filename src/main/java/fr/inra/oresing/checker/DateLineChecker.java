package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

public class DateLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_PATTERN = "pattern";
    public static final String PARAM_DATE_TIME_FORMATTER = "dateTimeFormatter";
    public static final String PARAM_COLUMN = "column";
    public static final String PARAM_VARIABLE_COMPONENT_KEY = "variableComponentKey";
    public static final String PARAM_DATE = "date";
    public static final String PATTERN_DATE_REGEXP = "^date:.{19}:";

    private final VariableComponentKey variableComponentKey;

    private final String column;

    private final DateTimeFormatter dateTimeFormatter;

    private final String pattern;
    public static String sortableDateToFormattedDate(String formattedDate){
        return formattedDate.replaceAll(PATTERN_DATE_REGEXP, "");
    }

    public DateLineChecker(VariableComponentKey variableComponentKey, String pattern) {
        this.variableComponentKey = variableComponentKey;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        this.pattern = pattern;
        this.column="";
    }

    public DateLineChecker(String column, String pattern) {
        this.column = column;
        this.variableComponentKey=null;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        this.pattern = pattern;
    }

    @Override
    public VariableComponentKey getVariableComponentKey() {
        return variableComponentKey;
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
                    variableComponentKey==null? PARAM_COLUMN : PARAM_VARIABLE_COMPONENT_KEY, variableComponentKey==null?column:variableComponentKey,
                    PARAM_DATE, date
            );
            validationCheckResult = DateValidationCheckResult.success(params);
        } catch (DateTimeParseException e) {
            validationCheckResult = DateValidationCheckResult.error("invalidDate", ImmutableMap.of("variableComponentKey", getVariableComponentKey()==null?getColumn():getVariableComponentKey(), "pattern", pattern, "value", value));
        }
        return validationCheckResult;
    }

    @Override
    public String getColumn() {
        return column;
    }
}
