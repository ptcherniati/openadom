package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;

import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_REFTYPE = "refType";

    private final VariableComponentKey variableComponentKey;

    private final String reference;

    private final ImmutableMap<String, UUID> referenceValues;

    ReferenceLineChecker(VariableComponentKey variableComponentKey, String reference, ImmutableMap<String, UUID> referenceValues) {
        this.variableComponentKey = variableComponentKey;
        this.reference = reference;
        this.referenceValues = referenceValues;
    }

    @Override
    public ReferenceValidationCheckResult check(String value) {
        ReferenceValidationCheckResult validationCheckResult;
        if (referenceValues.containsKey(value)) {
            validationCheckResult = ReferenceValidationCheckResult.success(getVariableComponentKey(), referenceValues.get(value));
        } else {
            validationCheckResult = ReferenceValidationCheckResult.error("invalidReference", ImmutableMap.of("variableComponentKey", variableComponentKey, "referenceValues", referenceValues, "value", value));
        }
        return validationCheckResult;
    }

    @Override
    public VariableComponentKey getVariableComponentKey() {
        return variableComponentKey;
    }

    public String getRefType() {
        return reference;
    }
}
