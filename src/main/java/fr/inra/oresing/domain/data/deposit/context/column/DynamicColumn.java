package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class DynamicColumn extends Column {

    /**
     * Les colonnes dynamiques sont représentées sous forme de Map dont la clé est la clé hiérarchique correspondant au référentiel qui décrit cette colonne dynamique
     */
    private final Ltree expectedHierarchicalKey;

    /**
     * Cette colonne dynamique a été générée par une ligne de référentiel, donc il faut lier la donnée à ce référentiel
     */
    private final Map.Entry<String, RefsLinkedToValue> refsLinkedToEntryToAdd;

    public DynamicColumn(final DataColumn referenceColumn, final ComponentPresenceConstraint presenceConstraint, final Ltree expectedHierarchicalKey, final Map.Entry<String, RefsLinkedToValue> refsLinkedToEntryToAdd, final ComputedValueUsage computedValueUsage, TransformationConfiguration defaultValue) {
        super(referenceColumn, presenceConstraint, computedValueUsage, defaultValue);
        this.expectedHierarchicalKey = expectedHierarchicalKey;
        this.refsLinkedToEntryToAdd = refsLinkedToEntryToAdd;
    }

    @Override
    public void pushValue(final String cellContent, final DataDatum referenceDatum, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        final DataColumnIndexedValue existingReferenceColumnIndexedValue;
        Map<Ltree, String> values;
        if (referenceDatum.contains(getReferenceColumn())) {
            existingReferenceColumnIndexedValue = (DataColumnIndexedValue) referenceDatum.get(getReferenceColumn());
            Map<Ltree, String> existingValues = existingReferenceColumnIndexedValue.values();
            values = new LinkedHashMap<>(existingValues);
        } else {
            values = new LinkedHashMap<>();
        }
        values.put(expectedHierarchicalKey, cellContent);
        final DataColumnIndexedValue newReferenceColumnIndexedValue = new DataColumnIndexedValue(values);
        referenceDatum.put(getReferenceColumn(), newReferenceColumnIndexedValue);
        refsLinkedTo
                .computeIfAbsent(refsLinkedToEntryToAdd.getKey(), k -> new HashMap<>())
                .computeIfAbsent(Column.COLUMN_IN_COLUMN_PATTERN.formatted(getReferenceColumn().column(), refsLinkedToEntryToAdd.getValue().hierarchicalKey()), k->new HashMap<>())
                .computeIfAbsent(refsLinkedToEntryToAdd.getValue().hierarchicalKey().getSql(), k-> new LinkedLines(refsLinkedToEntryToAdd.getValue().uuids()));
    }

    @Override
    public String getCsvCellContent(final DataDatum referenceDatum) {
        final DataColumnIndexedValue referenceColumnIndexedValue = (DataColumnIndexedValue) referenceDatum.get(getReferenceColumn());
        return referenceColumnIndexedValue.values().get(expectedHierarchicalKey);
    }

    @Override
    public int hashCode() {
        return getExpectedHeader().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final DynamicColumn dynamicColumn) {
            return getExpectedHeader().equals(dynamicColumn.getExpectedHeader());
        }
        return false;
    }
}