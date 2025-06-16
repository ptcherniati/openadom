package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class DataRows {
    List<String> rowId;
    List<String> patternColumnName;
    Ltree naturalKey;
    Ltree hierarchicalKey;
    List<Map<String, ? extends FieldType<?>>> values;
    List<Map<String, Map<String, RefsLinkedToValue>>> refsLinkedTo;
    Long totalRows = -1L;
    Long rowNumber = -1L;
    List<String> allPatternColumnNames;
    List<RefsLinked> refsLinked;
}