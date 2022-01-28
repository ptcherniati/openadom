package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegularExpressionChecker implements CheckerOnOneVariableComponentLineChecker<RegularExpressionCheckerConfiguration> {

    private final String patternString;

    private final Predicate<String> predicate;
    private final CheckerTarget target;
    private final RegularExpressionCheckerConfiguration configuration;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public RegularExpressionChecker(CheckerTarget target, String patternString, RegularExpressionCheckerConfiguration configuration) {
        this.configuration = configuration;
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
    public RegularExpressionCheckerConfiguration getConfiguration() {
        return configuration;
    }
}