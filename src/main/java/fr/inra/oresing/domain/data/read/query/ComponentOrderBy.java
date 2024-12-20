package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;

import java.util.Map;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;

public record ComponentOrderBy(String componentKey, DataRepository.Order order,
                               ComponentType sqlType) implements ComponentOrderByForExport {
    public ComponentOrderBy(final String componentKey, final DataRepository.Order order, final ComponentType sqlType) {

        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        this.sqlType = sqlType == null ? new ComponentTextType() : sqlType;
        this.componentKey = componentKey;
        this.order = order == null ? DataRepository.Order.ASC : order;
    }


    public Stream<String> toValue(
            String language,
            DataRepositoryWithBuffer dataRepository,
            Map<String, FieldType> dataRowValues,
            StandardDataDescription dataDescription
    ) {
        String componentKey = componentKey();
        FieldType fieldType = dataRowValues.get(componentKey);
        final String value = fieldType.toString();
        String valueString = valueToString(language, dataRepository, dataDescription,  fieldType);
        return Stream.of(valueString);
    }
}
