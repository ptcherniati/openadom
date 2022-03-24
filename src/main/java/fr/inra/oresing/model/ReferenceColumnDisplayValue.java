package fr.inra.oresing.model;

import fr.inra.oresing.rest.ReferenceImporterContext;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne a une seule valeur associée ({@link fr.inra.oresing.checker.Multiplicity#ONE}).
 */
@Value
public class ReferenceColumnDisplayValue implements ReferenceColumnValue<String, ReferenceColumnDisplayValue.ReferenceColumnDisplayValueForLocale> {

    private static final ReferenceColumnDisplayValue EMPTY = new ReferenceColumnDisplayValue();

    ReferenceColumnDisplayValueForLocale value = null;

    public ReferenceColumnDisplayValue() {
    }

    /**
     * Un {@link ReferenceColumnDisplayValue} vide (valeur non renseignée ?)
     */
    public static ReferenceColumnDisplayValue empty() {
        return EMPTY;
    }

    @Override
    public Collection<String> getValuesToCheck() {
        return null;
    }

    @Override
    public ReferenceColumnValue<String, ReferenceColumnDisplayValueForLocale> transform(Function<String, String> transformation) {
        return null;
    }

    @Override
    public String toValueString(ReferenceImporterContext referenceImporterContext, String referencedColumn, String key) {
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

    @Getter
    @Setter
    @ToString
    public static class ReferenceColumnDisplayToReplaceSingleValue extends ReferenceColumnDisplayToReplaceValue<String> {

    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceColumnDisplayToReplaceArrayValue extends ReferenceColumnDisplayToReplaceValue<List<String>> {

    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceColumnDisplayToReplaceMapValue extends ReferenceColumnDisplayToReplaceValue<Map<String, String>> {

    }

}