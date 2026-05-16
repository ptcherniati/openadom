package fr.inra.oresing.domain.data.read;

import com.google.common.collect.*;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.BuildColumns;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.apache.commons.csv.CSVRecord;

import java.util.*;
import java.util.function.Function;

public record DataHeaderReader(
        DataDatum constantValues,
        BuildColumns buildColumns,
        PublishContext.PublishContextBuilder publishContextBuilder,
        StandardDataDescription dataDescription
) {
    public DataHeaderReader(
            BuildColumns buildColumns,
            PublishContext.PublishContextBuilder publishContextBuilder,
            StandardDataDescription dataDescription
    ) {
        this(
                new DataDatum(),
                buildColumns,
                publishContextBuilder,
                dataDescription
        );
    }

    public DataHeaderReader {
        Objects.requireNonNull(buildColumns);
        Objects.requireNonNull(dataDescription);
        Objects.requireNonNull(publishContextBuilder);
    }

    private void addConstants(final ConstantComponent constant, final FieldType<?> value) {
        switch (value) {
            case final ListType<?> listType ->
                    constantValues().put(new DataColumn(constant.componentKey()), new DataColumnMultipleValue(listType.getValue()));
            case final MapType mapType -> throw new IllegalArgumentException("NO MAP HERE");
            case null, default ->
                    constantValues().put(new DataColumn(constant.componentKey()), new DataColumnSingleValue(value));
        }
    }

    private static int getColumnNumber(ImmutableList<String> headerRow, ConstantComponent constant) {
        final ColumnConstantHeader columnConstantHeader = (ColumnConstantHeader) constant.constantImportHeader();
        return switch (columnConstantHeader) {
            case final ColumnConstantHeaderByColumnNumber columnConstantHeaderByColumnNumber ->
                    columnConstantHeaderByColumnNumber.columnNumber();
            case final ColumnConstantHeaderByHeaderName columnConstantHeaderByHeaderName ->
                    headerRow.indexOf(columnConstantHeaderByHeaderName.headerName()) + 1;
        };
    }

    private void readPreHeaders(final Iterator<CSVRecord> linesIterator) {
        final Map<String, ConstantComponent> constantComponents = dataDescription().getComponentByType(ConstantComponent.class);
        final int headerLine = dataDescription().headerLine();
        final ImmutableSetMultimap<Integer, ConstantComponent> perRowNumberConstants = constantComponents.values().stream()
                .filter(constantComponent -> constantComponent.constantImportHeader() instanceof FileColumnConstantHeader)
                .collect(ImmutableSetMultimap.toImmutableSetMultimap(ConstantComponent::rowNumber, Function.identity()));
        final List<List<String>> preHeaderRows = new LinkedList<>();
        for (int lineNumber = 1; lineNumber < headerLine; lineNumber++) {
            final CSVRecord row = linesIterator.next();
            final ImmutableSet<ConstantComponent> constantDescriptions = perRowNumberConstants.get(lineNumber);
            preHeaderRows.add(row.stream().map(String::trim).toList());
            constantDescriptions.forEach(constant -> {
                final int columnNumber = ((FileColumnConstantHeader) constant.constantImportHeader()).columnNumber();
                final String valueInFile = row.size() >= columnNumber ? row.get(columnNumber - 1) : "".trim();
                final FieldType<String> value = StringType.getStringTypeFromStringValue(valueInFile);
                addConstants(constant, value);
            });
        }
        constantComponents.values().stream()
                .filter(constantComponent -> constantComponent.constantImportHeader() instanceof SubmissionConstantHeader)
                .forEach(constantComponent -> addConstants(
                        constantComponent,
                        StringType.getStringTypeFromStringValue(
                                Optional.of(publishContextBuilder().build())
                                        .map(PublishContext::fileOrUUID)
                                        .map(FileOrUUID::binaryfiledataset)
                                        .map(BinaryFileDataset::getRequiredAuthorizations)
                                        .map(requiredAuthorizations -> requiredAuthorizations.get(constantComponent.componentKey()))
                                        .map(list -> list.getFirst().getSql())
                                        .orElseThrow(() -> new IllegalArgumentException("no entry for constant submission"))
                        )));
        publishContextBuilder().withPreHeaderRow(preHeaderRows);
    }

    /**
     *
     */
    private ImmutableList<String> readHeaderRows(final Iterator<CSVRecord> linesIterator) {
        final CSVRecord headerRow = linesIterator.next();
        ImmutableList<String> headersForRow = Streams.stream(headerRow)
                .map(String::trim)
                .collect(ImmutableList.toImmutableList());
        headersForRow = InvalidDatasetContentException.checkHeader(
                headersForRow,
                buildColumns().expectedHeaders(),
                buildColumns().mandatoryHeaders(),
                ImmutableMultiset.copyOf(headersForRow),
                buildColumns().patternColumnFactory(),
                dataDescription().headerLine(),
                dataDescription().allowUnexpectedColumns()
        );
        publishContextBuilder().withHeaderRow(headersForRow);
        return headersForRow;
    }

    /**
     *
     */
    private void readPostHeaders(final ImmutableList<String> headerRow, final Iterator<CSVRecord> linesIterator) {
        final Map<String, ConstantComponent> constantComponents = dataDescription().getComponentByType(ConstantComponent.class);
        final int headerLine = dataDescription().headerLine();
        final Integer firstRowLine = dataDescription().firstRowLine();
        final ImmutableSetMultimap<Integer, ConstantComponent> perRowNumberConstants = constantComponents.values().stream()
                .filter(constantComponent -> constantComponent.constantImportHeader() instanceof ColumnConstantHeader)
                .collect(ImmutableSetMultimap.toImmutableSetMultimap(ConstantComponent::rowNumber, Function.identity()));
        final List<List<String>> postHeaderRows = new LinkedList<>();
        for (int lineNumber = headerLine + 1; lineNumber < firstRowLine; lineNumber++) {
            final CSVRecord row = linesIterator.next();
            final ImmutableSet<ConstantComponent> constantDescriptions = perRowNumberConstants.get(lineNumber);
            postHeaderRows.add(row.stream().map(String::trim).toList());
            constantDescriptions.forEach(constant -> {
                final int columnNumber = getColumnNumber(headerRow, constant);
                final String columnValue = columnNumber > 0 ? row.get(columnNumber - 1) : "";
                final FieldType<String> value = StringType.getStringTypeFromStringValue((row.size() >= columnNumber ? columnValue : "").trim());
                final String componentKey = constant.componentKey();
                addConstants(constant, value);
            });
        }
        publishContextBuilder().withPostHeaderRow(postHeaderRows);
    }

    public ImmutableList<String> readHeader(final Iterator<CSVRecord> linesIterator) {
        readPreHeaders(linesIterator);
        final ImmutableList<String> columns = readHeaderRows(linesIterator);
        readPostHeaders(columns, linesIterator);
        return columns;
    }

    public RowWithReferenceDatum addConstantsToRow(
            final RowWithReferenceDatum rowWithReferenceDatum
    ) {
        final ImmutableMap<DataColumn, DataColumnValue> values = ImmutableMap.<DataColumn, DataColumnValue>builder()
                .putAll(constantValues().values())
                .putAll(rowWithReferenceDatum.referenceDatum().values())
                .build();
        final DataDatum datum = new DataDatum(values);
        return new RowWithReferenceDatum(
                rowWithReferenceDatum.lineNumber(),
                rowWithReferenceDatum.patternColumnName(),
                datum,
                rowWithReferenceDatum.refsLinkedTo()
        );
    }
}