package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import fr.inra.oresing.model.ReferenceValue;
import lombok.Value;

@Value
public class HierarchicalReferenceAsTree {

    ImmutableSetMultimap<ReferenceValue, ReferenceValue> tree;

    ImmutableSet<ReferenceValue> roots;

    public ImmutableSet<ReferenceValue> getChildren(ReferenceValue referenceValue) {
        return getTree().get(referenceValue);
    }
}
