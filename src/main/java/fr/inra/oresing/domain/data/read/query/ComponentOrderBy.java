package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
