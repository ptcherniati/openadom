package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.assertj.core.util.Strings;

public class RequiredChecker implements ILineCheckerDecorator{
    public static final String PARAMS_REQUIRED = "required";
    CheckerOnOneVariableComponentLineChecker checker;
    public static final RequiredChecker notRequiredChecker(CheckerOnOneVariableComponentLineChecker checker){
        return new RequiredChecker(checker, false);
    }
    public static final RequiredChecker requiredChecker(CheckerOnOneVariableComponentLineChecker checker){
        return new RequiredChecker(checker, true);
    }
    boolean required = false;
    @Override
    public ValidationCheckResult check(String value) {
        if(Strings.isNullOrEmpty(value)){
            if(!required){
                return DefaultValidationCheckResult.success();
            }
            return DefaultValidationCheckResult.error(getTarget().getInternationalizedKey("requiredValue"), ImmutableMap.of("target", getTarget().getTarget()));
        }
        return checker.check(value);
    }

    private RequiredChecker(CheckerOnOneVariableComponentLineChecker checker, boolean required) {
        this.checker = checker;
        this.required = required;
    }

    private RequiredChecker(CheckerOnOneVariableComponentLineChecker checker) {
        this.checker = checker;
    }

    @Override
    public CheckerOnOneVariableComponentLineChecker getChecker() {
        return checker;
    }
}