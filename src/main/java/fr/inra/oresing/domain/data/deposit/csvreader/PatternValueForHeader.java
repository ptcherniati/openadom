package fr.inra.oresing.domain.data.deposit.csvreader;

import fr.inra.oresing.domain.data.RefsLinkedToValue;

import java.util.List;
import java.util.Map;

public record PatternValueForHeader(String header, String cellContent, List<String> adjacentCellContent,
                                    Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
}