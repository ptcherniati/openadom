package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface CheckerOnOneVariableComponentLineChecker extends LineChecker {

    VariableComponentKey getVariableComponentKey();
    String getColumn();

    @Override
    default ValidationCheckResult check(Map<VariableComponentKey, String> values) {
        String value = values.get(getVariableComponentKey());
        return check(value);
    }

    @Override
    default ValidationCheckResult checkReference(Map<String, String> values) {
        String value = values.get(getColumn());
        return check(value);
    }

    ValidationCheckResult check(String value);
}
