package fr.inra.oresing.model;

import com.google.common.base.Preconditions;
import lombok.Value;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link fr.inra.oresing.checker.Multiplicity#MANY}).
 */
@Value
public class ReferenceColumnMultipleValue implements ReferenceColumnValue<Set<String>> {
    public static final String CSV_CELL_SEPARATOR = ",";

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
    public ReferenceColumnValue<Set<String>> transform(Function<String, String> transformation) {
        Set<String> transformedValues = values.stream().map(transformation).collect(Collectors.toSet());
        return new ReferenceColumnMultipleValue(transformedValues);
    }

    @Override
    public String getAsContentForCsvCell() {
        String csvCellContent = values.stream()
                .peek(value -> Preconditions.checkState(value.contains(CSV_CELL_SEPARATOR), value + " contient " + CSV_CELL_SEPARATOR))
                .collect(Collectors.joining(CSV_CELL_SEPARATOR));
        return csvCellContent;
    }

    @Override
    public String toJsonForFrontend() {
        String jsonContent = values.stream()
                .peek(value -> Preconditions.checkState(value.contains(COLLECTION_AS_JSON_STRING_SEPARATOR), value + " contient " + COLLECTION_AS_JSON_STRING_SEPARATOR))
                .collect(Collectors.joining(COLLECTION_AS_JSON_STRING_SEPARATOR));
        return jsonContent;
    }
}
