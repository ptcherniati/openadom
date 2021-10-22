package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegularExpressionChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_PATTERN = "pattern";

    private final String patternString;

    private final Predicate<String> predicate;
    private CheckerTarget target;
    private Map<String, String> params;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public RegularExpressionChecker(CheckerTarget target, String patternString, Map<String, String> params) {
        this.params = params;
        this.target = target;
        this.patternString = patternString;
        predicate = Pattern.compile(patternString).asMatchPredicate();
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        if (predicate.test(value)) {
            validationCheckResult = DefaultValidationCheckResult.success();
        } else {
            validationCheckResult = DefaultValidationCheckResult.error(
                    getTarget().getInternationalizedKey("patternNotMatched"), ImmutableMap.of(
                            "target", target.getTarget(),
                            "pattern", patternString,
                            "value", value));
        }
        return validationCheckResult;
    }

    @Override
    public Map<String, String> getParams() {
        return params;
    }
}