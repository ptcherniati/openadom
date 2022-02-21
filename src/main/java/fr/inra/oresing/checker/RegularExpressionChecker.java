package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RegularExpressionChecker implements CheckerOnOneVariableComponentLineChecker<RegularExpressionCheckerConfiguration> {

    private final String patternString;

    private final Predicate<String> predicate;
    @JsonIgnore
    private final LineTransformer transformer;
    private final CheckerTarget target;
    private final RegularExpressionCheckerConfiguration configuration;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public RegularExpressionChecker(CheckerTarget target, String patternString, RegularExpressionCheckerConfiguration configuration, LineTransformer transformer) {
        this.configuration = configuration;
        this.target = target;
        this.patternString = patternString;
        predicate = Pattern.compile(patternString).asMatchPredicate();
        this.transformer = transformer;
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

    @Override
    public LineTransformer getTransformer() {
        return transformer;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }
}