package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

public class IntegerChecker implements CheckerOnOneVariableComponentLineChecker {

    private final VariableComponentKey variableComponentKey;

    public IntegerChecker(VariableComponentKey variableComponentKey) {
        this.variableComponentKey = variableComponentKey;
    }

    @Override
    public VariableComponentKey getVariableComponentKey() {
        return variableComponentKey;
    }

    @Override
    public ValidationCheckResult check(String value) {
        ValidationCheckResult validationCheckResult;
        try {
            Integer.parseInt(value);
            validationCheckResult = DefaultValidationCheckResult.success();
        } catch (NumberFormatException e) {
            validationCheckResult = DefaultValidationCheckResult.error("invalidInteger", ImmutableMap.of("variableComponentKey", variableComponentKey, "value", value));
        }
        return validationCheckResult;
    }
}
