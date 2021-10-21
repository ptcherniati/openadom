package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker {

    public static final String PARAM_REFTYPE = "refType";
    private final String reference;
    public ImmutableMap<String, UUID> referenceValues;
    public ImmutableMap<String, String> display;
    private Map<String, String> params;
    private CheckerTarget target;
    public ReferenceLineChecker(CheckerTarget target, String reference, ImmutableMap<String, UUID> referenceValues, ImmutableMap<String, String> display, Map<String, String> params) {
        this.params = params;
        this.target = target;
        this.reference = reference;
        this.referenceValues = referenceValues;
        this.display = display;
    }

    public ImmutableMap<String, UUID> getReferenceValues() {
        return referenceValues;
    }

    public void setReferenceValues(ImmutableMap<String, UUID> referenceValues) {
        this.referenceValues = referenceValues;
    }

    public CheckerTarget getTarget() {
        return this.target;
    }

    @Override
    public ReferenceValidationCheckResult check(String value) {
        ReferenceValidationCheckResult validationCheckResult;
        if (referenceValues.containsKey(value)) {
            validationCheckResult = ReferenceValidationCheckResult.success(target, referenceValues.get(value));
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