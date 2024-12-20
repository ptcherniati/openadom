package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

import java.util.Map;
import java.util.function.Function;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne a une seule valeur associée ({@link Multiplicity#ONE}).
 */
public record DataColumnDisplayValue(
        fr.inra.oresing.domain.data.DataColumnDisplayValue.ReferenceColumnDisplayValueForLocale value) implements DataColumnValue<String, DataColumnDisplayValue.ReferenceColumnDisplayValueForLocale> {

    private static final DataColumnDisplayValue EMPTY = new DataColumnDisplayValue(null);

    /**
     * Un {@link DataColumnDisplayValue} vide (valeur non renseignée ?)
     *
     */
    public static DataColumnDisplayValue empty() {
        return EMPTY;
    }

    @Override
    public FieldType getValuesToCheck() {
        return null;
    }

    @Override
    public DataColumnValue<String, ReferenceColumnDisplayValueForLocale> transform(final Function<FieldType, FieldType> transformation) {
        return null;
    }

    @Override
    public String toValueString(final DataImporterContext referenceImporterContext, final String referencedColumn, final String key) {
        return null;
    }

    @Override
    public ReferenceColumnDisplayValueForLocale toJsonForFrontend() {
        return null;
    }

    @Override
    public String toJsonForDatabase() {
        return null;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceColumnDisplayValueForLocale {
        private String pattern;
        private String toStringValue;
        private Map<String, String> types;
        private Map<String, ReferenceColumnDisplayToReplaceValue> values;
    }

    @Getter
    @Setter
    @ToString
    public abstract static class ReferenceColumnDisplayToReplaceValue<T> {
        T value;
    }

}