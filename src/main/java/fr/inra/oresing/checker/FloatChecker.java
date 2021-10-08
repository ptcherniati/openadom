package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

public class FloatChecker implements CheckerOnOneVariableComponentLineChecker {
    private CheckerTarget target;
    public CheckerTarget getTarget(){
        return this.target;
    }

    public FloatChecker(CheckerTarget target) {
        this.target = target;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            Float.parseFloat(value);
            validationCheckResult = DefaultValidationCheckResult.success();
        } catch (NumberFormatException e) {
            validationCheckResult = DefaultValidationCheckResult.error(
                    getTarget().getInternationalizedKey("invalidFloat"), ImmutableMap.of(
                            "target", target.getTarget(),
                            "value", value));
        }
        return validationCheckResult;
    }
}