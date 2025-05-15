package fr.inra.oresing.domain.data.deposit.validation.transformer;

import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.*;

import java.util.Map;
import java.util.function.Function;

public interface TransformOneLineElementTransformer extends LineTransformer {

    CheckerTarget target();

    @Override
    default Datum transform(final Datum datum) {
        final String componentKey = ((DataColumn) target()).column();
        final FieldType<?> value = datum.get(componentKey);
        final FieldType<?> transformedValue = transform(datum, value);
        final Datum transformedDatum = Datum.copyOf(datum);
        transformedDatum.put(componentKey, transformedValue);
        return transformedDatum;
    }

    @Override
    default DataDatum transform(final DataDatum referenceDatum, Map<String, Object> context) {
        final DataColumn referenceColumn = (DataColumn) target();
        final DataColumnValue referenceColumnValue;
        if (referenceDatum.contains(referenceColumn)) {
            referenceColumnValue = referenceDatum.get(referenceColumn);
        } else {
            // ici, on est dans le cas où on applique une transformation sur un colonne
            // qui n'existe pas. Elle a été déclarée comme colonne devant subir une transformation
            // alors que ce n'est pas une colonne du référentiel passé.
            // Comme il faut quand même appliquer la transformation, on part de rien
            referenceColumnValue = DataColumnSingleValue.empty();
        }
        final Function<FieldType<?>, FieldType<?>> fn = value -> transform(referenceDatum, value);
        final DataColumnValue transformedReferenceColumnValue = referenceColumnValue.transform(fn);
        final DataDatum transformedDatum = DataDatum.copyOf(referenceDatum);
        transformedDatum.put(referenceColumn, transformedReferenceColumnValue);
        return transformedDatum;
    }

    FieldType<?> transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, FieldType<?> value);
}