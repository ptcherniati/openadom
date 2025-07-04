package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.PatternComponent;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import fr.inra.oresing.domain.data.deposit.context.column.Column;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @param allPatternColumnNames Long totalRows;Long rowNumber;
 */
public record DataRow(
        List<String> rowId,
        List<String> patternColumnName,
        Ltree naturalKey,
        Ltree hierarchicalKey,
        Map<String, FieldType<?>> values,
        Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo,
        List<String> allPatternColumnNames,
        List<RefsLinked> refsLinked
) {
    public static DataRow of(StandardDataDescription application, DataRows dataRows) {
        List<String> patternComponentKeys = Optional.of(application)
                .map(StandardDataDescription::componentDescriptions)
                .stream().flatMap(descriptions -> descriptions.entrySet().stream()
                        .filter(component -> component.getValue() instanceof PatternComponent)
                        .map(Map.Entry::getKey)
                ).toList();
        Map<String, FieldType<?>> values = new HashMap<>(dataRows.getValues().getFirst());
        Map<String, ListType<? extends FieldType<?>>> listTypeMap = patternComponentKeys.stream()
                .map(componentKey -> {

                            final ListType<MapType<?, ?>> listTypes = new ListType<>(new MapType(Map.of()));
                            dataRows.getValues().stream()
                                    .filter(value -> value.containsKey(componentKey))
                                    .map(value -> value.get(componentKey))
                                    .map(MapType.class::cast)
                                    .forEach(listTypes::add);
                            return new AbstractMap.SimpleEntry<String, ListType>(componentKey, listTypes);
                        }
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        values.putAll(listTypeMap);
        Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo = new HashMap<>();
        for (int i = 0; i < dataRows.getPatternColumnName().size(); i++) {
            String patternColumnName = dataRows.getPatternColumnName().get(i);
            final List<Map<String, Map<String, RefsLinkedToValue>>> refsLinkedToValues = Optional.ofNullable(dataRows)
                    .map(DataRows::getRefsLinkedTo)
                    .orElse(List.of());
            Map<String, Map<String, RefsLinkedToValue>> refsLinkedto = refsLinkedToValues.contains(i)?refsLinkedToValues
                    .get(i):Map.of();
            for (Map.Entry<String, Map<String, RefsLinkedToValue>> refsLinkedtoEntryByReference : refsLinkedto.entrySet()) {
                String reference = refsLinkedtoEntryByReference.getKey();
                Map<String, RefsLinkedToValue> refsLinkedtoByComponent = refsLinkedTo.computeIfAbsent(reference, k -> new HashMap<>());
                for (Map.Entry<String, RefsLinkedToValue> refsLinkedtoEntryByComponent : refsLinkedtoEntryByReference.getValue().entrySet()) {
                    String componentKey = refsLinkedtoEntryByComponent.getKey();
                    if (componentKey.contains(Column.COLUMN_IN_COLUMN_SEPARATOR)) {
                        refsLinkedtoByComponent.remove(componentKey);
                        componentKey = Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentKey, patternColumnName);
                    }
                    refsLinkedtoByComponent
                            .put(componentKey, refsLinkedtoEntryByComponent.getValue());

                }
            }
        }
        return new DataRow(
                dataRows.getRowId(),
                dataRows.getPatternColumnName(),
                dataRows.getNaturalKey(),
                dataRows.getHierarchicalKey(),
                values,
                refsLinkedTo,
                //dataRows.getTotalRows(),
                //dataRows.getRowNumber(),
                dataRows.getAllPatternColumnNames(),
                dataRows.getRefsLinked()
        );
    }
}