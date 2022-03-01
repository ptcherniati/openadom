package fr.inra.oresing.checker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.rest.validationcheckresults.ReferenceValidationCheckResult;
import fr.inra.oresing.transformer.LineTransformer;

import java.util.UUID;

public class ReferenceLineChecker implements CheckerOnOneVariableComponentLineChecker<ReferenceLineCheckerConfiguration> {

    private final String reference;
    private ImmutableMap<Ltree, UUID> referenceValues;
    private final ReferenceLineCheckerConfiguration configuration;
    private final CheckerTarget target;

    @JsonIgnore
    private final LineTransformer transformer;

    public ReferenceLineChecker(CheckerTarget target, String reference, ImmutableMap<Ltree, UUID> referenceValues, ReferenceLineCheckerConfiguration configuration, LineTransformer transformer) {
        this.configuration = configuration;
        this.target = target;
        this.reference = reference;
        this.referenceValues = referenceValues;
        this.transformer = transformer;
    }

    public ImmutableMap<Ltree, UUID> getReferenceValues() {
        return referenceValues;
    }

    public void setReferenceValues(ImmutableMap<Ltree, UUID> referenceValues) {
        this.referenceValues = referenceValues;
    }

    public CheckerTarget getTarget() {
        return this.target;
    }

    @Override
    public ReferenceValidationCheckResult check(String rawValue) {
        ReferenceValidationCheckResult validationCheckResult;
        Ltree valueAsLtree = Ltree.fromSql(rawValue); // pas d'échappement car on est sûrement déjà passé par codify
        if (referenceValues.containsKey(valueAsLtree)) {
            validationCheckResult = ReferenceValidationCheckResult.success(target, rawValue, valueAsLtree, referenceValues.get(valueAsLtree));
        } else {
            validationCheckResult = ReferenceValidationCheckResult.error(target, rawValue, getTarget().getInternationalizedKey("invalidReference"), ImmutableMap.of(
                    "target", target.getTarget(),
                    "referenceValues", referenceValues,
                    "refType", reference,
                    "value", rawValue));
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

    @Override
    public LineTransformer getTransformer() {
        return transformer;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }
}
