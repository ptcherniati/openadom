package fr.inra.oresing.model;

import fr.inra.oresing.rest.ReferenceImporterContext;
import lombok.Value;

import java.util.Set;
import java.util.function.Function;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne a une seule valeur associée ({@link fr.inra.oresing.checker.Multiplicity#ONE}).
 */
@Value
public class ReferenceColumnSingleValue implements ReferenceColumnValue<String, String> {

    private static final ReferenceColumnSingleValue EMPTY = new ReferenceColumnSingleValue("");

    String value;

    /**
     * Un {@link ReferenceColumnSingleValue} vide (valeur non renseignée ?)
     */
    public static ReferenceColumnSingleValue empty() {
        return EMPTY;
    }

    @Override
    public String toJsonForDatabase() {
        return value;
    }

    @Override
    public Set<String> getValuesToCheck() {
        return Set.of(value);
    }

    @Override
    public ReferenceColumnSingleValue transform(Function<String, String> transformation) {
        String transformedValue = transformation.apply(value);
        return new ReferenceColumnSingleValue(transformedValue);
    }

    @Override
    public String toValueString(ReferenceImporterContext referenceImporterContext, String referencedColumn, String locale) {
        return referenceImporterContext.getDisplayByReferenceAndNaturalKey(referencedColumn, value, locale);
    }

    @Override
    public String toJsonForFrontend() {
        return value;
    }
}