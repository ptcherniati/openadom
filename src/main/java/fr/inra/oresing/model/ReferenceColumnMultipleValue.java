package fr.inra.oresing.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import lombok.Value;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Permet de stocker la valeur pour une colonne d'un référentiel lorsque cette colonne est multi-valuées ({@link fr.inra.oresing.checker.Multiplicity#MANY}).
 */
@Value
public class ReferenceColumnMultipleValue implements ReferenceColumnValue<Set<String>> {
    private static final String CSV_CELL_SEPARATOR = ",";

    private static final String COLLECTION_AS_JSON_STRING_SEPARATOR = ",";

    Set<String> values;

    /**
     * Étant donné le contenu d'une cellule d'un fichier CSV contenant plusieurs valeurs, on split
     */
    public static ReferenceColumnMultipleValue parseCsvCellContent(String csvCellContent) {
        Set<String> values = Splitter.on(CSV_CELL_SEPARATOR)
                .splitToStream(csvCellContent)
                .collect(Collectors.toSet());
        return new ReferenceColumnMultipleValue(values);
    }

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
