package fr.inra.oresing.rest.data.extraction;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.RefsLinked;
import fr.inra.oresing.domain.data.read.query.ComponentOrderByForExport;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public record DataCsvRowBuilder(
        String language,
        DataRepository dataRepository,
        StandardDataDescription dataDescription,
        boolean horizontalDisplay) {
    public List<String> getCsvRow(List<RefsLinked> refsLinkeds, Map<String, FieldType<?>> dataRowValues,
                                  List<ComponentOrderByForExport> columns) {
        Function<ComponentOrderByForExport, Stream<String>> toValue = componentOrderBy -> componentOrderBy.toValue(refsLinkeds, language(), dataRowValues, dataDescription());
        return columns
                .stream()
                .flatMap(toValue)
                .toList();
    }
}