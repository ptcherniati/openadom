package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class Column implements Comparable<Column> {
    public static final String COLUMN_IN_COLUMN_SEPARATOR = "::";
    public static final String COLUMN_IN_COLUMN_PATTERN = "%s::%s";
    public static final String __VALUE__ = "__VALUE__";
    public static final String __COLUMN_NAME__ = "__COLUMN_NAME__";
    public static final String __ORIGINAL_COLUMN_NAME__ = "__ORIGINAL_COLUMN_NAME__";

    @Getter
    private final DataColumn referenceColumn;
    @Getter
    private final ComponentPresenceConstraint presenceConstraint;
    @Getter
    private final ComputedValueUsage computedValueUsage;

    public Column(final DataColumn referenceColumn, final ComponentPresenceConstraint presenceConstraint, final ComputedValueUsage computedValueUsage) {
        super();
        this.referenceColumn = referenceColumn;
        this.presenceConstraint = presenceConstraint;
        this.computedValueUsage = computedValueUsage;
    }

    public static Column staticColumnDescriptionToColumn(final DataColumn referenceColumn,
                                                         String headerForColumn,
                                                         final ComponentPresenceConstraint presenceConstraint,
                                                         final Multiplicity multiplicity,
                                                         final TransformationConfiguration defaultValue) {
        return switch (multiplicity){
            case ONE -> new OneValueStaticColumn(
                    referenceColumn,
                    headerForColumn,
                    presenceConstraint,
                    ComputedValueUsage.NOT_COMPUTED
            ) {
                @Override
                public String getExpectedHeader() {
                    return Optional.ofNullable(headerForColumn)
                            .orElseGet(referenceColumn::column);
                }

                @Override
                public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            };
            case MANY -> new ManyValuesStaticColumn(
                    referenceColumn,
                    headerForColumn,
                    presenceConstraint,
                    ComputedValueUsage.NOT_COMPUTED
            ) {
                @Override
                public String getExpectedHeader() {
                    return Optional.ofNullable(headerForColumn)
                            .orElseGet(referenceColumn::column);
                }

                @Override
                public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                    throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
                }
            };
        };
    }

    public static Column staticPatternColumnDescriptionToColumn(final DataColumn referenceColumn,
                                                                String headerForColumn,
                                                                String headerInFile,
                                                                final ComponentPresenceConstraint presenceConstraint,
                                                                final Multiplicity multiplicity,
                                                                final List<Column> qualifierColumns,
                                                                final List<Column> adjacentColumns,
                                                                final TransformationConfiguration defaultValue) {
        Column column;
        column = new OneValueStaticPatternColumn(
                referenceColumn,
                headerForColumn,
                headerInFile,
                multiplicity,
                defaultValue,
                presenceConstraint,
                ComputedValueUsage.NOT_COMPUTED,
                qualifierColumns,
                adjacentColumns
        ) {
            @Override
            public String getExpectedHeader() {
                return Optional.ofNullable(headerForColumn)
                        .orElseGet(referenceColumn::column);
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                throw new UnsupportedOperationException("pas de valeur par défaut pour " + referenceColumn);
            }
        };
        return column;
    }

    public Column as(String columnHeader) {
        return columnHeader.equals(getReferenceColumn().column()) ? this : null;
    }

    public boolean canHandle(final String header) {
        return isExpected() && getExpectedHeader().equals(header);
    }

    public abstract void pushValue(String cellContent, DataDatum referenceDatum, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo);

    public abstract String getCsvCellContent(DataDatum referenceDatum);

    public abstract String getExpectedHeader();

    public boolean isMandatory() {
        return presenceConstraint.isMandatory();
    }

    public boolean isExpected() {
        return presenceConstraint.isExpected();
    }

    public abstract Optional<DataColumnValue> computeValue(DataDatum referenceDatum);

    @Override
    public int compareTo(final Column o) {
        return getReferenceColumn().column().compareTo(o.getReferenceColumn().column());
    }

    @Override
    public int hashCode() {
        return getReferenceColumn().column().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final Column column) {
            return getReferenceColumn().column().equals(column.getReferenceColumn().column());
        }
        return false;
    }
}