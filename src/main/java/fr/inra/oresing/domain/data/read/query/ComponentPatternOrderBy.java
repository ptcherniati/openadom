package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public record ComponentPatternOrderBy(String componentKey, String qualifierKey, DataRepository.Order order,
                                      ComponentType sqlType,
                                      List<ComponentOrderBy> qualifiersColumns) implements ComponentOrderByForExport {
    @Override
    public Stream<String> toValue(String language, DataRepositoryWithBuffer dataRepository, Map<String, FieldType> dataRowValues, StandardDataDescription dataDescription) {
        String componentKey = componentKey();
        FieldType fieldType = dataRowValues.get(componentKey);
        Optional<MapType> valueOpt = ((ListType) fieldType).getValue().stream().filter(mapType -> qualifierKey().equals(((MapType) mapType).getValue().get(Column.__ORIGINAL_COLUMN_NAME__).toString())).findFirst();
        if (valueOpt.isEmpty()) {
            return Stream.empty();
        }
        List<String> values = new ArrayList<>();
        values.add(valueToString(language, dataRepository, dataDescription, (FieldType) valueOpt.get().getValue().get(Column.__VALUE__)));
        qualifiersColumns().stream()
                .map(qualifier -> qualifier.valueToString(language, dataRepository, dataDescription, (FieldType) valueOpt.get().getValue()
                .get( qualifier.componentKey().split(Column.COLUMN_IN_COLUMN_SEPARATOR)[1])))
                .forEach(values::add);
        return values.stream();
    }
}
