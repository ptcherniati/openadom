package fr.inra.oresing.transformer;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.model.VariableComponentKey;

public interface TransformOneLineElementTransformer extends LineTransformer {

    CheckerTarget getTarget();

    @Override
    default Datum transform(Datum datum) {
        VariableComponentKey variableComponentKey = (VariableComponentKey) getTarget().getTarget();
        String value = datum.get(variableComponentKey);
        String transformedValue = transform(datum, value);
        Datum transformedDatum = Datum.copyOf(datum);
        transformedDatum.put(variableComponentKey, transformedValue);
        return transformedDatum;
    }

    @Override
    default ReferenceDatum transform(ReferenceDatum referenceDatum) {
        ReferenceColumn referenceColumn = (ReferenceColumn) getTarget().getTarget();
        String value = referenceDatum.get(referenceColumn);
        String transformedValue = transform(referenceDatum, value);
        ReferenceDatum transformedDatum = ReferenceDatum.copyOf(referenceDatum);
        transformedDatum.put(referenceColumn, transformedValue);
        return transformedDatum;
    }

    String transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, String value);
}