package fr.inra.oresing.checker;

import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Set;

public interface LineChecker<C extends LineCheckerConfiguration> {

    ValidationCheckResult check(Datum values);
    Set<ValidationCheckResult> checkReference(ReferenceDatum referenceDatum);
    C getConfiguration();
}