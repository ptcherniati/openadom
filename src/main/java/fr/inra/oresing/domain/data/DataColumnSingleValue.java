package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import lombok.Value;

import java.util.function.UnaryOperator;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne a une seule valeur associée ({@link Multiplicity#ONE}).
 */
@Value
public class DataColumnSingleValue implements DataColumnValue<FieldType<?>, FieldType<?>> {

    private static final DataColumnSingleValue EMPTY = new DataColumnSingleValue(StringType.getStringTypeFromStringValue(""));

    FieldType<?> value;

    public DataColumnSingleValue(final FieldType<?> stringType) {
        super();
        value = stringType;
    }

    /**
     * Un {@link DataColumnSingleValue} vide (valeur non renseignée ?)
     */
    public static DataColumnSingleValue empty() {
        return EMPTY;
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return value;
    }

    @Override
    public FieldType<?> getValuesToCheck() {
        return value;
    }

    @Override
    public DataColumnSingleValue transform(final UnaryOperator<FieldType<?>> transformation) {
        final FieldType<?> transformedValue = transformation.apply(value);
        return new DataColumnSingleValue(transformedValue);
    }

    @Override
    public String toValueString(final AsynchroneFileImporterContext referenceImporterContext, final String referencedColumn, final String locale) {
        return referenceImporterContext.getDisplayNamesByReferenceAndNaturalKey(referencedColumn, value.toString(), locale);
    }

    @Override
    public FieldType<?> toJsonForFrontend() {
        return value.copy();
    }
}