package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_REFTYPE = "refType";

    public void setReferenceValues(ImmutableMap<String, UUID> referenceValues) {
        this.referenceValues = referenceValues;
    }

    public ImmutableMap<String, UUID> referenceValues;
    private final String reference;
    public  Map<String, String> params;
    private CheckerTarget target;
    public ReferenceLineChecker(CheckerTarget target, String reference, ImmutableMap<String, UUID> referenceValues, Map<String, String> params) {
        this.params = params;
        this.target = target;
        this.reference = reference;
        this.referenceValues = referenceValues;
    }

    public ImmutableMap<String, UUID> getReferenceValues() {
        return referenceValues;
    }

    public CheckerTarget getTarget() {
        return this.target;
    }

    @Override
    public ReferenceValidationCheckResult check(String value) {
        ReferenceValidationCheckResult validationCheckResult;
        if (referenceValues.containsKey(value)) {
            validationCheckResult = ReferenceValidationCheckResult.success(target.getType().getType(), referenceValues.get(value));
        } else {
            validationCheckResult = ReferenceValidationCheckResult.error(getTarget().getInternationalizedKey("invalidReference"), ImmutableMap.of(
                    "target", target.getTarget(),
                    "referenceValues", referenceValues,
                    "refType", reference,
                    "value", value));
        }
        return validationCheckResult;
    }

    public String getRefType() {
        return reference;
    }

    @Override
    public Map<String, String> getParams() {
        return params;
    }
}