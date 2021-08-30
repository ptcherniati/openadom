package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegularExpressionChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_PATTERN = "pattern";

    private final VariableComponentKey variableComponentKey;

    private final String patternString;

    private final Predicate<String> predicate;

    public RegularExpressionChecker(VariableComponentKey variableComponentKey, String patternString) {
        this.variableComponentKey = variableComponentKey;
        this.patternString = patternString;
        predicate = Pattern.compile(patternString).asMatchPredicate();
    }

    @Override
    public VariableComponentKey getVariableComponentKey() {
        return variableComponentKey;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        if (predicate.test(value)) {
            validationCheckResult = DefaultValidationCheckResult.success();
        } else {
            validationCheckResult = DefaultValidationCheckResult.error("patternNotMatched", ImmutableMap.of("variableComponentKey", getVariableComponentKey(), "pattern", patternString, "value", value));
        }
        return validationCheckResult;
    }
}
