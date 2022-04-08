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
}