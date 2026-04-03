package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.persistence.RefsLinked;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record DynamicComponentOrderBy(String componentKey,
                                      Map<String, ComponentOrderBy> dynamicColumns) implements ComponentOrderByForExport {
    @Override
    public Stream<String> toValue(List<RefsLinked> refsLinkeds, String language, Map<String, FieldType<?>> dataRowValues, StandardDataDescription dataDescription) {
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