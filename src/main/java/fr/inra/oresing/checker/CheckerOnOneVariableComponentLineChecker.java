package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface CheckerOnOneVariableComponentLineChecker extends LineChecker {

    CheckerTarget getTarget();

    @Override
    default ValidationCheckResult check(Map<VariableComponentKey, String> values) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = values.get(variableComponentKey);
        ValidationCheckResult check = check(value);
        return check;
    }

    @Override
    default ValidationCheckResult checkReference(Map<String, String> values) {
        String value = values.get(getTarget().getTarget());
        return check(value);
    }

    ValidationCheckResult check(String value);
}