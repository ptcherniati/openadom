package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.FieldType;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Lignes de données renvoyées par une requête de lecture.
 *
 * <p>Value object domaine pur : aucune dépendance Spring ou persistence.
 */
@Value
public class DataRows {
    List<String> rowId;
    List<String> patternColumnName;
    Ltree naturalKey;
    Ltree hierarchicalKey;
    List<Map<String, FieldType<?>>> values;
    List<Map<String, Map<String, RefsLinkedToValue>>> refsLinkedTo;
    Long totalRows = -1L;
    Long rowNumber = -1L;
    List<String> allPatternColumnNames;
    List<RefsLinked> refsLinked;
}