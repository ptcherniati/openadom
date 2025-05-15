package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;

import java.util.Map;

public abstract class OneValueStaticColumn extends Column {

    public OneValueStaticColumn(final DataColumn referenceColumn, final String headerForColumn, final ComponentPresenceConstraint presenceConstraint, final ComputedValueUsage computedValueUsage) {
        super(referenceColumn, headerForColumn, presenceConstraint, computedValueUsage);
    }

    @Override
    public void pushValue(final String cellContent, final DataDatum referenceDatum, final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        final DataColumnValue<FieldType<?>, FieldType<?>> referenceColumnValue = new DataColumnSingleValue(StringType.getStringTypeFromStringValue(cellContent));
        referenceDatum.put(getReferenceColumn(), referenceColumnValue);
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
}