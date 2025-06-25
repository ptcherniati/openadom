package fr.inra.oresing.rest.data.extraction;

import com.opencsv.CSVWriter;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.DynamicComponent;
import fr.inra.oresing.domain.application.configuration.PatternComponent;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DataCsvHeaderWriter(
        CSVWriter writer,
        Comparator<ComponentOrderByForExport> comparator,
        Function<String, String> getInternationalizedHeader,
        DataRepository dataRepository,
        List<ComponentOrderByForExport> orderedColumns,
        StandardDataDescription dataDescription,
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns,
        boolean horizontalDisplay) {
    public DataCsvHeaderWriter(
            CSVWriter writer,
            Comparator<ComponentOrderByForExport> comparator,
            Function<String, String> getInternationalizedHeader,
            DataRepository dataRepository,
            StandardDataDescription dataDescription,
            Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns,
            boolean horizontalDisplay) {
        this(writer,
                comparator,
                getInternationalizedHeader,
                dataRepository,
                new ArrayList<>(),
                dataDescription,
                internationalizedSortedColumns,
                horizontalDisplay);
    }

    DataRow writeHeader(DataRow dataRow) {
        if (CollectionUtils.isNotEmpty(orderedColumns())) {
            return dataRow;
        }
        List<ComponentOrderByForExport> orderedColumns = buildInternationalizedColumns(dataRow);
        orderedColumns().addAll(orderedColumns);
        List<String> columns = buildHeaderNames(orderedColumns);
        // Écrire l'en-tête
        writer().writeNext(columns.toArray(new String[]{}));
        return dataRow;
    }

    private List<String> buildHeaderNames(List<ComponentOrderByForExport> orderedColumns) {
        return orderedColumns.stream()
                .flatMap(columnName -> switch (columnName) {
                    case ComponentOrderBy componentOrderBy -> componentOrderByGetHeader(componentOrderBy);
                    case ComponentPatternOrderBy componentPatternOrderBy ->
                            headerForPatternComponent(componentPatternOrderBy);
                    case DynamicComponentOrderBy dynamicComponentOrderBy ->
                            dynamicComponentOrderBy.dynamicColumns().values().stream()
                                    .map(this::internationalizeColumnName);
                    case ComponentPatternValueOrderBy componentPatternValue ->
                            headerForPatternComponentValue(componentPatternValue);
                })
                .toList();
    }

    private Stream<String> componentOrderByGetHeader(ComponentOrderByForExport componentOrderBy) {
        Configuration.InternationalizedSortedColumn internationalizedSortedColumn = internationalizedSortedColumns.get(componentOrderBy.componentKey());
        String exportHeader = Optional.ofNullable(internationalizedSortedColumn)
                .map(Configuration.InternationalizedSortedColumn::header)
                .map(getInternationalizedHeader())
                .orElse(null);
        return exportHeader == null ? Stream.of(Objects.requireNonNull(internationalizedSortedColumn).header()) : Stream.of(exportHeader);

    }

    private String internationalizeColumnName(ComponentOrderBy qualifierColumn) {
        return getInternationalizedHeader().apply(qualifierColumn.componentKey());
    }

    private Stream<String> headerForPatternComponent(ComponentPatternOrderBy componentPatternOrderBy) {
        List<String> internationalizedPatternColumns = new LinkedList<>();
        internationalizedPatternColumns.add(componentPatternOrderBy.qualifierKey());
        componentPatternOrderBy.qualifiersColumns().stream()
                .map(this::internationalizeColumnName)
                .forEach(internationalizedPatternColumns::add);
        return internationalizedPatternColumns.stream();
    }

    private Stream<String> headerForPatternComponentValue(ComponentPatternValueOrderBy componentPatternOrderBy) {
        List<String> internationalizedPatternColumns = new LinkedList<>();
        internationalizedPatternColumns.add(getInternationalizedHeader().apply(componentPatternOrderBy.componentKey()));
        componentPatternOrderBy.allColumns().stream()
                .map(this::internationalizeColumnName)
                .forEach(internationalizedPatternColumns::add);
        return internationalizedPatternColumns.stream();
    }

    private LinkedList<ComponentOrderByForExport> buildInternationalizedColumns(DataRow dataRow) {
        return internationalizedSortedColumns().values().stream()
                .flatMap(internationalizedSortedColumn -> switch (internationalizedSortedColumn.componentDescription()) {
                    case DynamicComponent dynamicComponent -> columnsForDynamicComponent(dynamicComponent);
                    case PatternComponent patternComponent ->
                            columnsForPatternComponent(internationalizedSortedColumn, patternComponent, dataRow);
                    default -> columnsForDefaultComponent(internationalizedSortedColumn);
                })
                .sorted(comparator())
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private Stream<ComponentOrderBy> columnsForDefaultComponent(Configuration.InternationalizedSortedColumn internationalizedSortedColumn) {
        return Stream.of(new ComponentOrderBy(
                        internationalizedSortedColumn.componentDescription().componentKey(),
                        fr.inra.oresing.persistence.DataRepository.Order.ASC,
                        dataDescription().getTypeForComponentKey(internationalizedSortedColumn.componentDescription().componentKey())
                )
        );
    }

    private Stream<ComponentOrderByForExport> columnsForPatternComponent(
            Configuration.InternationalizedSortedColumn internationalizedSortedColumn,
            PatternComponent patternComponent,
            DataRow dataRow) {
        if (horizontalDisplay()) {
            return dataRow.allPatternColumnNames().stream()
                    .filter(columnName -> columnName.matches(patternComponent.patternForComponents()))
                    .map(columnName -> {
                        List<ComponentOrderBy> qualifierColumns = new LinkedList<>();
                        patternComponent.patternComponentAdjacents().forEach((key, value) -> qualifierColumns.add(
                                new ComponentOrderBy(
                                        value.exportHeaderName(),
                                        fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                        dataDescription().getTypeForPatternComponentKeyAndComponentKey(patternComponent.componentKey(), value.componentKey())
                                )
                        ));
                        return new ComponentPatternOrderBy(
                                patternComponent.componentKey(),
                                columnName,
                                fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                dataDescription().getTypeForComponentKey(internationalizedSortedColumn.componentDescription().componentKey()),
                                qualifierColumns.stream().sorted(comparator()).toList()
                        );
                    });
        } else {
            Set<ComponentOrderBy> qualifierColumns = new LinkedHashSet<>();
            Set<ComponentOrderBy> adjacentColumns = new LinkedHashSet<>();
            patternComponent.patternComponentQualifiers()
                    .forEach((key, value) -> qualifierColumns.add(
                            new ComponentOrderBy(
                                    value.exportHeaderName(),
                                    fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                    dataDescription().getTypeForPatternComponentKeyAndComponentKey(patternComponent.componentKey(), value.componentKey())
                            )
                    ));
            patternComponent.patternComponentAdjacents()
                    .forEach((key, value) -> adjacentColumns.add(
                            new ComponentOrderBy(
                                    value.exportHeaderName(),
                                    fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                    dataDescription().getTypeForPatternComponentKeyAndComponentKey(patternComponent.componentKey(), value.componentKey())
                            )
                    ));
            return dataDescription().componentDescriptions().entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof PatternComponent)
                    .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), (PatternComponent) entry.getValue()))
                    .map(entry -> {
                        String componentKey = entry.getKey();
                        PatternComponent component = entry.getValue();
                        return new ComponentPatternValueOrderBy(
                                patternComponent.componentKey(),
                                Column.__VALUE__,
                                fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                dataDescription().getTypeForComponentKey(componentKey),
                                qualifierColumns,
                                adjacentColumns
                        );
                    });
        }
    }

    private Stream<DynamicComponentOrderBy> columnsForDynamicComponent(DynamicComponent dynamicComponent) {
        String componentKey = dynamicComponent.componentKey();
        String referenceName = dynamicComponent.reference();
        String referenceColumnToLookForHeader = dynamicComponent.referenceColumnToLookForHeader();
        ComponentType typeForComponentKey = dataDescription().getTypeForComponentKey(componentKey);
        Map<String, ComponentOrderBy> dynamicColumns = dataRepository()
                .findAllByReferenceTypeStream(referenceName)
                .sorted(Comparator.comparing(dataValue -> dataValue.getNaturalKey().getSql()))
                .collect(Collectors.toMap(
                        dataValue -> dataValue.getNaturalKey().getSql(),
                        dataValue -> new ComponentOrderBy(
                                dataValue
                                        .getRefValues()
                                        .get(new DataColumn(referenceColumnToLookForHeader))
                                        .getValuesToCheck().toString(),
                                fr.inra.oresing.persistence.DataRepository.Order.ASC,
                                typeForComponentKey
                        ),
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
        return Stream.of(new DynamicComponentOrderBy(
                componentKey,
                dynamicColumns
        ));
    }
}