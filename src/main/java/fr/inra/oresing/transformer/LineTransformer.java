package fr.inra.oresing.transformer;

import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;

public interface LineTransformer {

    Datum transform(Datum values);

    ReferenceDatum transform(ReferenceDatum referenceDatum);

}