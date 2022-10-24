package fr.inra.oresing.checker;

import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.util.Set;

@Value
public class ReferenceLineCheckerDisplay implements LineChecker {
    ReferenceLineChecker referenceLineChecker;
    ReferenceValue referenceValues;

    public ReferenceLineCheckerDisplay(ReferenceLineChecker referenceLineChecker, ReferenceValue referenceValues) {
        this.referenceLineChecker = referenceLineChecker;
        this.referenceValues = referenceValues;
    }

    @Override
    public ValidationCheckResult check(Datum values) {
        return null;
    }

    @Override
    public Set<ValidationCheckResult> checkReference(ReferenceDatum referenceDatum) {
        return null;
    }

    @Override
    public LineCheckerConfiguration getConfiguration() {
        return null;
    }

    @Override
    public ValidationCheckResult checkWithoutTransformation(Datum values) {
        return check(values);
    }
}