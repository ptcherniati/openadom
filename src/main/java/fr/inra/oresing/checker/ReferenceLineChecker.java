package fr.inra.oresing.checker;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.VariableComponentKey;

import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_REFTYPE = "refType";
    public static final String PARAM_KEYSCOLUMNSSEARCH = "keysColumnsSearch";
    public static final String PARAM_PATTERNKEY = "patternKey";

    private final VariableComponentKey variableComponentKey;

    private final String reference;
    private  String patternKey;

    private final ImmutableMap<String, UUID> referenceValues;

    ReferenceLineChecker(VariableComponentKey variableComponentKey, String reference, ImmutableMap<String, UUID> referenceValues) {
        this.variableComponentKey = variableComponentKey;
        this.reference = reference;
        this.referenceValues = referenceValues;
    }

    public ReferenceLineChecker(VariableComponentKey variableComponentKey, String reference, ImmutableMap<String, UUID> referenceValues, String patternKey) {
        this.variableComponentKey = variableComponentKey;
        this.reference = reference;
        this.referenceValues = referenceValues;
        this.patternKey= patternKey;
    }

    @Override
    public ReferenceValidationCheckResult check(String value) {
        ReferenceValidationCheckResult validationCheckResult;
        if(!Strings.isNullOrEmpty(patternKey)){
            value=patternKey.replace("$?",value);
        }
        if (referenceValues.containsKey(value)) {
            validationCheckResult = ReferenceValidationCheckResult.success(referenceValues.get(value));
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
