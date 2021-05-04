package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.apache.commons.lang3.time.FastDateFormat;

import java.text.ParseException;

public class DateLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_PATTERN = "pattern";

    private final VariableComponentKey variableComponentKey;

    private final FastDateFormat fastDateFormat;

    private final String pattern;

    public DateLineChecker(VariableComponentKey variableComponentKey, String pattern) {
        this.variableComponentKey = variableComponentKey;
        this.fastDateFormat = FastDateFormat.getInstance(pattern);
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
            fastDateFormat.parse(value);
            validationCheckResult = new DefaultValidationCheckResult(true, null, null);
        } catch (ParseException e) {
            validationCheckResult = new DefaultValidationCheckResult(false, "invalidDate", ImmutableMap.of("variableComponentKey", variableComponentKey, "pattern", pattern, "value", value));
        }
        return validationCheckResult;
    }
}
