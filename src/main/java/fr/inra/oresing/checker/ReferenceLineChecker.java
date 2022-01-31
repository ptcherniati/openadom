package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;

import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker<ReferenceLineCheckerConfiguration> {

    private final String reference;
    public ImmutableMap<String, UUID> referenceValues;
    private final ReferenceLineCheckerConfiguration configuration;
    private final CheckerTarget target;
    public ReferenceLineChecker(CheckerTarget target, String reference, ImmutableMap<String, UUID> referenceValues, ReferenceLineCheckerConfiguration configuration) {
        this.configuration = configuration;
        this.target = target;
        this.reference = reference;
        this.referenceValues = referenceValues;
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
            validationCheckResult = ReferenceValidationCheckResult.success(value, target, referenceValues.get(value));
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
    public ReferenceLineCheckerConfiguration getConfiguration() {
        return configuration;
    }
}