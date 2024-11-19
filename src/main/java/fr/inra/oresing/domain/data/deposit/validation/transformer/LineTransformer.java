package fr.inra.oresing.domain.data.deposit.validation.transformer;

import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.Datum;
import fr.inra.oresing.domain.data.DataDatum;

import java.util.Map;

public interface LineTransformer extends LineChecker.Transformer {

    Datum transform(Datum values);

    DataDatum transform(DataDatum referenceDatum, Map<String, Object> context);

}