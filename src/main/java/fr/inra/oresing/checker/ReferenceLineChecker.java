package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;

import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_REFTYPE = "refType";


    private final VariableComponentKey variableComponentKey;

    @Override
    public String getColumn() {
        return column;
    }

    private final String column;

    private final String reference;

    private final ImmutableMap<String, UUID> referenceValues;

    ReferenceLineChecker(VariableComponentKey variableComponentKey, String reference, ImmutableMap<String, UUID> referenceValues) {
        this.variableComponentKey = variableComponentKey;
        this.column = "";
        this.reference = reference;
        this.referenceValues = referenceValues;
    }

    ReferenceLineChecker(String column, String reference, ImmutableMap<String, UUID> referenceValues) {
        this.variableComponentKey = null;
        this.column = column;
        this.reference = reference;
        this.referenceValues = referenceValues;
    }

    @Override
    public ReferenceValidationCheckResult check(String value) {
        ReferenceValidationCheckResult validationCheckResult;
        if (referenceValues.containsKey(value)) {
            validationCheckResult = variableComponentKey!=null?
                    ReferenceValidationCheckResult.success(variableComponentKey, referenceValues.get(value)):
                    ReferenceValidationCheckResult.success(column, referenceValues.get(value));
        } else{
            validationCheckResult = variableComponentKey!=null?
                    ReferenceValidationCheckResult.error("invalidReference", ImmutableMap.of("variableComponentKey", variableComponentKey, "referenceValues", referenceValues, "value", value)):
                    ReferenceValidationCheckResult.error("invalidReference", ImmutableMap.of("column", column, "referenceValues", referenceValues, "value", value));
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
