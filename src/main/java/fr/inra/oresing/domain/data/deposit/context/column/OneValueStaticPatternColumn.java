package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import lombok.Getter;

import java.util.*;

public abstract class OneValueStaticPatternColumn extends Column {

    @Getter
    private final Multiplicity multiplicity;
    @Getter
    private final TransformationConfiguration defaultValue;

    @Getter
    private final String headerInFile;
    private final List<Column> qualifierColumns;
    private final List<Column> adjacentColumns;
    public OneValueStaticPatternColumn(
            final DataColumn referenceColumn,
            final String headerForColumn,
            final String headerInField,
            final Multiplicity multiplicity,
            final TransformationConfiguration defaultValue,
            final ComponentPresenceConstraint presenceConstraint,
            final ComputedValueUsage computedValueUsage,
            List<Column> qualifierColumns,
            List<Column> adjacentColumns) {
        super(referenceColumn, presenceConstraint, computedValueUsage);
        this.defaultValue = defaultValue;
        this.multiplicity = multiplicity;
        this.headerInFile = headerInField;
        this.qualifierColumns = qualifierColumns;
        this.adjacentColumns = adjacentColumns;
    }

    public int getAdjacentColumnsSize() {
        return adjacentColumns == null ? 0 : adjacentColumns.size();
    }

    @Override
    public Optional<DataColumnValue> computeValue(DataDatum referenceDatum) {
        return Optional.empty();
    }

    @Override
    public Column as(String columnHeader) {
        if (super.as(columnHeader) != null) {
            return this;
        }
        if (!columnHeader.contains(COLUMN_IN_COLUMN_SEPARATOR)) {
            return null;
        }
        List<String> patternOfColumn = Arrays.stream(columnHeader.split(COLUMN_IN_COLUMN_SEPARATOR)).toList();
        if (!getExpectedHeader().equals(patternOfColumn.get(0))) {
            return null;
        }
        if (getExpectedHeader().equals(__VALUE__)) {
            return this;
        }
        Optional<Column> matchingQualifierColumn = qualifierColumns.stream()
                .map(qualifierColumn -> qualifierColumn.as(patternOfColumn.get(1)))
                .filter(Objects::nonNull)
                .findFirst();
        if (matchingQualifierColumn.isPresent()) {
            return matchingQualifierColumn.get();
        }
        Optional<Column> matchingAdjacentColumn = adjacentColumns.stream()
                .filter(adjacentColumn -> adjacentColumn.getReferenceColumn().column().equals(patternOfColumn.get(1)))
                .findFirst();
        return matchingAdjacentColumn.orElse(null);
    }

    @Override
    public void pushValue(final String cellContent, final DataDatum referenceDatum, final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        Map<DataColumn, DataColumnValue> columnValues = new HashMap<>();
        columnValues.put(new DataColumn(Column.__VALUE__), new DataColumnSingleValue(StringType.getStringTypeFromStringValue(cellContent)));
        columnValues.putAll(referenceDatum.values());
        final DataColumnValue<Map<String, Object>, Map<String, Object>> referenceColumnValue = new DataColumnPatternValue(columnValues);
        referenceDatum.values().clear();
        referenceDatum.put(getReferenceColumn(), referenceColumnValue);
    }

    public DataDatum buildValue(
            final String headerName,
            final String cellContent,
            final DataDatum qualifierComponents,
            DataDatum adjacentComponents,
            final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        Map<DataColumn, DataColumnValue> columnValues = new HashMap<>();
        columnValues.put(new DataColumn(Column.__VALUE__), new DataColumnSingleValue(StringType.getStringTypeFromStringValue(cellContent)));
        columnValues.put(new DataColumn(Column.__COLUMN_NAME__), new DataColumnSingleValue(StringType.getStringTypeFromStringValue(headerName)));
        columnValues.put(new DataColumn(Column.__ORIGINAL_COLUMN_NAME__), new DataColumnSingleValue(StringType.getStringTypeFromStringValue(headerInFile)));
        columnValues.putAll(qualifierComponents.values());
        columnValues.putAll(adjacentComponents.values());
        final DataColumnValue<Map<String, Object>, Map<String, Object>> referenceColumnValue = new DataColumnPatternValue(columnValues);
        DataDatum datum = new DataDatum();
        datum.put(getReferenceColumn(), referenceColumnValue);
        return datum;
    }

    @Override
    public String getCsvCellContent(final DataDatum referenceDatum) {
        try {
            final DataColumnSingleValue referenceColumnSingleValue = (DataColumnSingleValue) referenceDatum.get(getReferenceColumn());
            return referenceColumnSingleValue.getValue().toString();
        } catch (final IllegalArgumentException iae) {
            return "NA";
        }
    }

    public DataDatum buildAdjacentComponents(List<String> adjacentComponentsValues) {
        Map<DataColumn, DataColumnValue> columnValues = new HashMap<>();
        for (int i = 0; i < adjacentColumns.size(); i++) {
            Column adjacentColumn = adjacentColumns.get(i);
            DataColumn dataColumn = adjacentColumn.getReferenceColumn();
            String value = adjacentComponentsValues.get(i);
            FieldType<?> fieldValue = Strings.isNullOrEmpty(value) ? StringType.getStringTypeFromStringValue("") : StringType.getStringTypeFromStringValue(value);
            DataColumnValue<FieldType<?>, FieldType<?>> dataColumnValue = new DataColumnSingleValue(fieldValue);
            columnValues.put(dataColumn, dataColumnValue);
        }
        return new DataDatum(columnValues);
    }
}