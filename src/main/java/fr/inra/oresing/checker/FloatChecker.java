package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public class FloatChecker implements CheckerOnOneVariableComponentLineChecker {
    private final CheckerTarget target;
    private final Map<String, String> params;

    public CheckerTarget getTarget(){
        return this.target;
    }

    public FloatChecker(CheckerTarget target, Map<String, String> params) {
        this.params = params;this.target = target;
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

    @Override
    public Map<String, String> getParams() {
        return params;
    }
}