package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.RefsLinked;

import java.util.*;
import java.util.stream.Stream;

public record ComponentPatternValueOrderBy(String componentKey, String qualifierKey, DataRepository.Order order,
                                           ComponentType sqlType,
                                           Set<ComponentOrderBy> qualifiersColumns,
                                           Set<ComponentOrderBy> adjacentColumns) implements ComponentOrderByForExport {
    private static Optional getMapType(ListType fieldType) {
        return fieldType.getValue().stream()
                .findFirst();
    }

    @Override
    public Stream<String> toValue(List<RefsLinked> refsLinkeds, String language, Map<String, FieldType<?>> dataRowValues, StandardDataDescription dataDescription) {
        String componentKey = componentKey();
        ListType fieldType = (ListType) dataRowValues.get(componentKey);

        Optional<MapType> patternMapTypeOpt = getMapType(fieldType);
        if (patternMapTypeOpt.isPresent()) {
            List<String> values = new LinkedList<>();
            Optional<String> valueopt = patternMapTypeOpt
                    .map(mapType -> getValue(refsLinkeds, language, dataDescription, mapType));
            values.add(valueopt.orElse(""));
            allColumns().stream()
                    .map(qualifier -> {
                        if (qualifier.componentKey().contains("::")) {
                            return getAdacentValue(refsLinkeds, language, dataDescription, qualifier, patternMapTypeOpt.get());
                        }
                        return getQualifierValue(refsLinkeds, language, dataDescription, qualifier, patternMapTypeOpt.get());
                    }).forEach(values::add);
            return values.stream();
        }
        return Stream.empty();
    }

    private String getAdacentValue(List<RefsLinked> refsLinkeds, String language, StandardDataDescription dataDescription, ComponentOrderBy qualifier, MapType patternMapTypeOpt) {
        String adjacentKey = qualifier.componentKey().split(Column.COLUMN_IN_COLUMN_SEPARATOR)[1];
        FieldType<?> adjacentField = (FieldType<?>) patternMapTypeOpt.getValue().get(adjacentKey);
        return valueToString(refsLinkeds, language, dataDescription, adjacentField);
    }

    private String getQualifierValue(List<RefsLinked> refsLinkeds, String language, StandardDataDescription dataDescription, ComponentOrderBy qualifier, MapType patternMapTypeOpt) {
        FieldType<?> adjacentField = (FieldType<?>) patternMapTypeOpt.getValue().get(qualifier.componentKey());
        return valueToString(refsLinkeds, language, dataDescription, adjacentField);
    }

    private String getValue(List<RefsLinked> refsLinkeds, String language, StandardDataDescription dataDescription, MapType mapType) {
        return valueToString(refsLinkeds, language, dataDescription, (FieldType<?>) mapType.getValue().get(Column.__VALUE__));
    }

    public List<ComponentOrderBy> allColumns() {
        Set<ComponentOrderBy> components = qualifiersColumns();
        components.addAll(adjacentColumns());
        return components.stream()
                .sorted(Comparator.comparing(ComponentOrderBy::order))
                .toList();
    }

}