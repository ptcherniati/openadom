package fr.inra.oresing.model;

import fr.inra.oresing.rest.ReferenceImporterContext;
import lombok.Value;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link fr.inra.oresing.checker.Multiplicity#MANY}).
 */
@Value
public class ReferenceColumnMultipleValue implements ReferenceColumnValue<Set<String>, Set<String>> {

    private static final String COLLECTION_AS_JSON_STRING_SEPARATOR = ",";

    Set<String> values;

    @Override
    public Set<String> toJsonForDatabase() {
        return values;
    }

    @Override
    public Set<String> getValuesToCheck() {
        return values;
    }

    @Override
    public ReferenceColumnMultipleValue transform(Function<String, String> transformation) {
        Set<String> transformedValues = values.stream().map(transformation).collect(Collectors.toSet());
        return new ReferenceColumnMultipleValue(transformedValues);
    }

    @Override
    public String toValueString(ReferenceImporterContext referenceImporterContext, String referencedColumn, String locale) {
        return values.stream()
                .map(s->referenceImporterContext.getDisplayByReferenceAndNaturalKey(referencedColumn, s, locale))
                .collect(Collectors.joining(",","[","]"));
    }

    @Override
    public Set<String> toJsonForFrontend() {
        return values;
    }
}