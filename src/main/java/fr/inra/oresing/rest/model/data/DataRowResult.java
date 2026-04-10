package fr.inra.oresing.rest.model.data;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.NullType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import fr.inra.oresing.persistence.DataRow;
import org.apache.commons.collections.keyvalue.DefaultMapEntry;

import java.util.*;
import java.util.stream.Collectors;


public record DataRowResult(
        List<String> rowId,
        String naturalKey,
        String hierarchicalKey,
        Map<String, Object> values,
        List<fr.inra.oresing.persistence.RefsLinked> refsLinkeds, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo,
        //Long totalRows,
        //Long rowNumber,
        Map<Object, Object> displaysForRow,
        List<String> allPatternColumnName
) {

    public static final String DEFAULT = "default";

    public static DataRowResult of(DataRow dataRow,
                                   ImmutableSet<String> variables,
                                   String locale) {
        final Map<String, Object> rows = new HashMap<>();
        for (final Map.Entry<String, FieldType<?>> componentEntry : dataRow.values().entrySet()) {
            final String component = componentEntry.getKey();
            if (variables.contains(component) || componentEntry.getKey().startsWith(DataColumn.DISPLAY)) {
                rows
                        .put(component, Optional.of(componentEntry)
                                .map(Map.Entry::getValue)
                                .map(FieldType::toJsonForFrontend)
                                .orElse(NullType.INSTANCE));
            }
        }
        // Build a lookup map (referenceType -> naturalKey -> RefsLinked) for O(1) access instead of O(n) scan
        Map<String, Map<String, fr.inra.oresing.persistence.RefsLinked>> refsLinkedMap =
                dataRow.refsLinked() != null
                        ? dataRow.refsLinked().stream()
                                .collect(Collectors.groupingBy(
                                        fr.inra.oresing.persistence.RefsLinked::referenceType,
                                        Collectors.toMap(
                                                r -> r.naturalKey().getSql(),
                                                r -> r,
                                                (existing, replacement) -> existing
                                        )
                                ))
                        : Map.of();
        Map<Object, Object> displaysForRow = dataRow.refsLinkedTo() != null
                ? dataRow.refsLinkedTo().entrySet().stream()
                .map(referenceEntry -> {
                    String referenceName = referenceEntry.getKey();
                    Map<String, fr.inra.oresing.persistence.RefsLinked> refsByNaturalKey =
                            refsLinkedMap.getOrDefault(referenceName, Map.of());
                    Map<Object, Object> naturalKeysDisplay = referenceEntry.getValue().values().stream()
                            .map(RefsLinkedToValue::hierarchicalKey)
                            .map(hierarchicalKey -> hierarchicalKey.getSql().replaceAll(".*[a-z]K", ""))
                            .map(naturalKey -> {
                                fr.inra.oresing.persistence.RefsLinked refsLinked = refsByNaturalKey.get(naturalKey);
                                String displayValue;
                                if (refsLinked != null) {
                                    displayValue = locale.equals(Locale.FRENCH.getLanguage()) ? refsLinked.__display_fr() : refsLinked.__display_en();
                                    if (displayValue == null) {
                                        displayValue = refsLinked.__display_default();
                                    }
                                } else {
                                    displayValue = naturalKey;
                                }
                                return new DefaultMapEntry(naturalKey, displayValue);
                            })
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));
                    return new DefaultMapEntry(referenceName, naturalKeysDisplay);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing))
                : Map.of();
        return new DataRowResult(dataRow.rowId(),
                dataRow.naturalKey().getSql(),
                dataRow.hierarchicalKey().getSql(),
                rows,
                dataRow.refsLinked(),
                dataRow.refsLinkedTo(),
                displaysForRow,
                dataRow.allPatternColumnNames());
    }
}