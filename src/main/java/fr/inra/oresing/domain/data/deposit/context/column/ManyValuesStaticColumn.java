package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.base.Splitter;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.List;
import java.util.Map;

public abstract class ManyValuesStaticColumn extends Column {

    public static final String CSV_CELL_SEPARATOR = ",";

    protected ManyValuesStaticColumn(final DataColumn referenceColumn, final ComponentPresenceConstraint presenceConstraint, final ComputedValueUsage computedValueUsage, TransformationConfiguration defaultValue) {
        super(referenceColumn, presenceConstraint, computedValueUsage, defaultValue);
    }

    @Override
    public void pushValue(final String cellContent, final DataDatum referenceDatum, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        final List<String> values = Splitter.on(CSV_CELL_SEPARATOR)
                .splitToStream(cellContent)
                .toList();
        final DataColumnValue referenceColumnValue = new DataColumnMultipleValue(values);
        referenceDatum.put(getReferenceColumn(), referenceColumnValue);
    }

    @Override
    public String getCsvCellContent(final DataDatum referenceDatum) {
        final DataColumnMultipleValue referenceColumnMultipleValue = (DataColumnMultipleValue) referenceDatum.get(getReferenceColumn());
        return referenceColumnMultipleValue.getCsvCellContent();
    }
}