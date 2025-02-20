package fr.inra.oresing.rest.data.extraction;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public record DataCsvRowBuilder(
        String language,
        DataRepositoryForBuffer dataRepositoryWithBuffer,
        StandardDataDescription dataDescription,
        boolean horizontalDisplay) {
    public List<String> getCsvRow(Map<String, FieldType> dataRowValues,
                                         List<ComponentOrderByForExport> columns) {
        Function<ComponentOrderByForExport, Stream<String>> toValue = componentOrderBy -> componentOrderBy.toValue(language(), dataRepositoryWithBuffer(), dataRowValues, dataDescription());
        return columns
                .stream()
                .flatMap(toValue)
                .toList();
    }
}
