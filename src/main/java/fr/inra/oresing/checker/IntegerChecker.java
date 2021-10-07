package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

public class IntegerChecker implements CheckerOnOneVariableComponentLineChecker {
    private CheckerTarget target;
    public CheckerTarget getTarget(){
        return this.target;
    }

    public IntegerChecker(CheckerTarget target) {
        this.target = target;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            Integer.parseInt(value);
            validationCheckResult = DefaultValidationCheckResult.success();
        } catch (NumberFormatException e) {
            validationCheckResult = DefaultValidationCheckResult.error(getTarget().getInternationalizedKey("invalidInteger"),
                    ImmutableMap.of("target",target.getTarget(),
                            "value", value));
        }
        return validationCheckResult;
    }
}