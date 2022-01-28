package fr.inra.oresing.checker;

import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Map;

public interface LineChecker<C extends LineCheckerConfiguration> {

    ValidationCheckResult check(Map<VariableComponentKey, String> values);
    ValidationCheckResult checkReference(Map<String, String> values);
    C getConfiguration();
}