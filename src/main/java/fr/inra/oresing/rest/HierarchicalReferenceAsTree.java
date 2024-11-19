package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import fr.inra.oresing.domain.data.DataValue;


public record HierarchicalReferenceAsTree(
        ImmutableSetMultimap<DataValue, DataValue> tree,
        ImmutableSet<DataValue> roots) {

    public ImmutableSet<DataValue> getChildren(final DataValue referenceValue) {
        return tree.get(referenceValue);
    }
}
