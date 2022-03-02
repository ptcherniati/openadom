package fr.inra.oresing.transformer;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.model.VariableComponentKey;

import java.util.function.Function;

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
        ReferenceColumnValue referenceColumnValue;
        if (referenceDatum.contains(referenceColumn)) {
            referenceColumnValue = referenceDatum.get(referenceColumn);
        } else {
            // ici, on est dans le cas où on applique une transformation sur un colonne
            // qui n'existe pas. Elle a été déclarée comme colonne devant subir une transformation
            // alors que ce n'est pas une colonne du référentiel passé.
            // Comme il faut quand même appliquer la transformation, on part de rien
            referenceColumnValue = ReferenceColumnSingleValue.empty();
        }
        Function<String, String> fn = value -> transform(referenceDatum, value);
        ReferenceColumnValue transformedReferenceColumnValue = referenceColumnValue.transform(fn);
        ReferenceDatum transformedDatum = ReferenceDatum.copyOf(referenceDatum);
        transformedDatum.put(referenceColumn, transformedReferenceColumnValue);
        return transformedDatum;
    }

    String transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, String value);
}