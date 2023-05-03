package fr.inra.oresing.model;

import com.google.common.base.Preconditions;
import fr.inra.oresing.rest.ReferenceImporterContext;
import lombok.Value;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link fr.inra.oresing.checker.Multiplicity#MANY}).
 */
@Value
 public class ReferenceColumnMultipleValue<U> implements ReferenceColumnValue<Set<U>, Set<String>> {

    private static final String COLLECTION_AS_JSON_STRING_SEPARATOR = ",";

    public ReferenceColumnMultipleValue(Set<U> values) {
        this.values = values;
    }

    Set<U> values;

    @Override
    public Set<U> toJsonForDatabase() {
        return values;
    }

    @Override
    public Set<String> getValuesToCheck() {
        return values.stream().map(Object::toString).collect(Collectors.toSet());
    }

    @Override
    public ReferenceColumnMultipleValue transform(Function<String, String> transformation) {
        Set<U> transformedValues = values.stream().map(Objects::toString).map(transformation).map(this::stringToValue).collect(Collectors.toSet());
        return new ReferenceColumnMultipleValue(transformedValues);
    }

    private U stringToValue(String s) {
        final U u = values.stream().findFirst().map(o -> (U) o).orElse(null);
        if(u instanceof Integer){
            return (U) (Integer.valueOf(s));
        }
        if(u instanceof Float){
            return (U) (Float.valueOf(s));
        }
        return (U) s;
    }

    @Override
    public String toValueString(ReferenceImporterContext referenceImporterContext, String referencedColumn, String locale) {
        return values.stream()
                .map(s->referenceImporterContext.getDisplayByReferenceAndNaturalKey(referencedColumn, s.toString(), locale))
                .collect(Collectors.joining(",","[","]"));
    }

    @Override
    public Set<String> toJsonForFrontend() {
        return values.stream().map(Object::toString).collect(Collectors.toSet());
    }

    public String getCsvCellContent() {
        return values.stream()
            .map(Object::toString)
            .peek(value -> Preconditions.checkState(!value.contains(ReferenceImporterContext.ManyValuesStaticColumn.CSV_CELL_SEPARATOR), value + " contient " + ReferenceImporterContext.ManyValuesStaticColumn.CSV_CELL_SEPARATOR))
            .collect(Collectors.joining(ReferenceImporterContext.ManyValuesStaticColumn.CSV_CELL_SEPARATOR));

    }
}