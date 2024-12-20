package fr.inra.oresing.rest.data.extraction;

import com.opencsv.CSVWriter;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.persistence.DataRepository;
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
        DataRepositoryWithBuffer dataRepositoryWithBuffer,
        List<ComponentOrderByForExport> orderedColumns,
        StandardDataDescription dataDescription,
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns) {
    public DataCsvHeaderWriter(
            CSVWriter writer,
            Comparator<ComponentOrderByForExport> comparator,
            Function<String, String> getInternationalizedHeader,
            DataRepositoryWithBuffer dataRepositoryWithBuffer,
            StandardDataDescription dataDescription,
            Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns) {
        this(writer, comparator, getInternationalizedHeader, dataRepositoryWithBuffer, new ArrayList<>(), dataDescription, internationalizedSortedColumns);
    }

    protected DataRow writeHeader(DataRow dataRow) {
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
                })
                .toList();
    }

    private Stream<String> componentOrderByGetHeader(ComponentOrderBy componentOrderBy) {
        Configuration.InternationalizedSortedColumn internationalizedSortedColumn = internationalizedSortedColumns.get(componentOrderBy.componentKey());
        String exportHeader = Optional.ofNullable(internationalizedSortedColumn)
                .map(Configuration.InternationalizedSortedColumn::header)
                .map(getInternationalizedHeader())
                .orElse(null);
        return exportHeader==null?Stream.of(internationalizedSortedColumn.header()):Stream.of(exportHeader);

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
                        DataRepository.Order.ASC,
                        dataDescription().getTypeForComponentKey(internationalizedSortedColumn.componentDescription().componentKey())
                )
        );
    }

    private Stream<ComponentPatternOrderBy> columnsForPatternComponent(
            Configuration.InternationalizedSortedColumn internationalizedSortedColumn,
            PatternComponent patternComponent,
            DataRow dataRow) {
        ListType<MapType> fieldType = (ListType<MapType>) dataRow.values().get(patternComponent.componentKey());
        return dataRow.allPatternColumnNames().stream()
                .filter(columnName -> columnName.matches(patternComponent.patternForComponents()))
                .map(columnName -> {
                    List<ComponentOrderBy> qualifierColumns = new LinkedList<>();
                    patternComponent.patternComponentAdjacents().entrySet()
                            .stream()
                            .forEach(qualifiersEntry -> qualifierColumns.add(
                                    new ComponentOrderBy(
                                            qualifiersEntry.getValue().exportHeaderName(),
                                            DataRepository.Order.ASC,
                                            dataDescription().getTypeForPatternComponentKeyAndComponentKey(patternComponent.componentKey(), qualifiersEntry.getValue().componentKey())
                                    )
                            ));
                    return new ComponentPatternOrderBy(
                            patternComponent.componentKey(),
                            columnName,
                            DataRepository.Order.ASC,
                            dataDescription().getTypeForComponentKey(internationalizedSortedColumn.componentDescription().componentKey()),
                            qualifierColumns.stream().sorted(comparator()).toList()
                    );
                });
    }

    private Stream<DynamicComponentOrderBy> columnsForDynamicComponent(DynamicComponent dynamicComponent) {
        String componentKey = dynamicComponent.componentKey();
        String referenceName = dynamicComponent.reference();
        String referenceColumnToLookForHeader = dynamicComponent.referenceColumnToLookForHeader();
        ComponentType typeForComponentKey = dataDescription().getTypeForComponentKey(componentKey);
        Map<String, ComponentOrderBy> dynamicColumns = dataRepositoryWithBuffer().repository().findAllByReferenceTypeStream(referenceName)
                .sorted(Comparator.comparing(dataValue -> ((DataValue) dataValue).getNaturalKey().getSql()))
                .collect(Collectors.toMap(
                        dataValue -> ((DataValue) dataValue).getNaturalKey().getSql(),
                        dataValue -> new ComponentOrderBy(
                                (
                                        (DataColumnValue) ((DataValue) dataValue)
                                                .getRefValues()
                                                .get(new DataColumn(referenceColumnToLookForHeader))
                                )
                                        .getValuesToCheck().toString(),
                                DataRepository.Order.ASC,
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
