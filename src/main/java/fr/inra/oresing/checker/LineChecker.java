package fr.inra.oresing.checker;

import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.rest.ValidationCheckResult;

public interface LineChecker<C extends LineCheckerConfiguration> {

    ValidationCheckResult check(Datum values);
    ValidationCheckResult checkReference(ReferenceDatum referenceDatum);
    C getConfiguration();
}