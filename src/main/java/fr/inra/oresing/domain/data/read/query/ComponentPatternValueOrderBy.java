package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.persistence.DataRepository;

import java.util.*;
import java.util.stream.Stream;

public record ComponentPatternValueOrderBy(String componentKey, String qualifierKey, DataRepository.Order order,
                                           ComponentType sqlType,
                                           Set<ComponentOrderBy> qualifiersColumns,
                                           Set<ComponentOrderBy> adjacentColumns) implements ComponentOrderByForExport {
    @Override
    public Stream<String> toValue(String language, DataRepositoryForBuffer dataRepository, Map<String, FieldType> dataRowValues, StandardDataDescription dataDescription) {
        String componentKey = componentKey();
        ListType fieldType = (ListType) dataRowValues.get(componentKey);

        Optional<MapType> patternMapTypeOpt = getMapType(fieldType);
        if (patternMapTypeOpt.isPresent()) {
            List<String> values = new LinkedList<>();
            Optional<String> valueopt = patternMapTypeOpt
                    .map(mapType -> getValue(language, dataRepository, dataDescription, mapType));
            values.add(valueopt.orElse(""));
            allColumns().stream()
                    .map(qualifier -> {
                        if(qualifier.componentKey().contains("::")) {
                            return getAdacentValue(language, dataRepository, dataDescription, qualifier, patternMapTypeOpt.get());
                        }
                        return getQualifierValue(language, dataRepository, dataDescription, qualifier, patternMapTypeOpt.get());
                    }).forEach(values::add);
            return values.stream();
        }
        return Stream.empty();
    }

    private String getAdacentValue(String language, DataRepositoryForBuffer dataRepository, StandardDataDescription dataDescription, ComponentOrderBy qualifier, MapType patternMapTypeOpt) {
        String adjacentKey = qualifier.componentKey().split(Column.COLUMN_IN_COLUMN_SEPARATOR)[1];
        FieldType adjacentField = (FieldType) patternMapTypeOpt.getValue().get(adjacentKey);
        return valueToString(language, dataRepository, dataDescription, adjacentField);
    }

    private String getQualifierValue(String language, DataRepositoryForBuffer dataRepository, StandardDataDescription dataDescription, ComponentOrderBy qualifier, MapType patternMapTypeOpt) {
        FieldType adjacentField = (FieldType) patternMapTypeOpt.getValue().get(qualifier.componentKey());
        return valueToString(language, dataRepository, dataDescription, adjacentField);
    }

    private String getValue(String language, DataRepositoryForBuffer dataRepository, StandardDataDescription dataDescription, MapType mapType) {
        return valueToString(language, dataRepository, dataDescription, (FieldType) mapType.getValue().get(Column.__VALUE__));
    }

    private static Optional getMapType(ListType fieldType) {
        return fieldType.getValue().stream()
                .findFirst();
    }

    public List<ComponentOrderBy> allColumns() {
        Set<ComponentOrderBy> components = qualifiersColumns();
        components.addAll(adjacentColumns());
        return components.stream()
                .sorted(Comparator.comparing(ComponentOrderBy::order))
                .toList();
    }

}