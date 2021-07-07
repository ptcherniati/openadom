package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_PATTERN = "pattern";

    private final VariableComponentKey variableComponentKey;

    private final DateTimeFormatter dateTimeFormatter;

    private final String pattern;

    public DateLineChecker(VariableComponentKey variableComponentKey, String pattern) {
        this.variableComponentKey = variableComponentKey;
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
            dateTimeFormatter.parse(value);
            validationCheckResult = DefaultValidationCheckResult.success();
        } catch (DateTimeParseException e) {
            validationCheckResult = DefaultValidationCheckResult.error("invalidDate", ImmutableMap.of("variableComponentKey", variableComponentKey, "pattern", pattern, "value", value));
        }
        return validationCheckResult;
    }
}
