package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;

import java.util.Map;
import java.util.stream.Stream;

public record DynamicComponentOrderBy(String componentKey, Map<String, ComponentOrderBy> dynamicColumns) implements ComponentOrderByForExport {
    @Override
    public Stream<String> toValue(String language, DataRepositoryForBuffer dataRepository, Map<String, FieldType<?>> dataRowValues, StandardDataDescription dataDescription) {
        String componentKey = componentKey();
        Map<String, StringType> values = (Map<String, StringType>) dataRowValues.get(componentKey).getValue();
        return dynamicColumns().keySet().stream()
                .map(values::get)
                .map(FieldType::toString);

    }
    @Override
    public ComponentType sqlType() {
        return new ComponentTextType();
    }

}