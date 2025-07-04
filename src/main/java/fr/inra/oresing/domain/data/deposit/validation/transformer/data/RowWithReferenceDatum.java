package fr.inra.oresing.domain.data.deposit.validation.transformer.data;

import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.RefsLinkedToValue;

import java.util.Map;

public record RowWithReferenceDatum(long lineNumber, String patternColumnName, DataDatum referenceDatum,
                                    Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
}