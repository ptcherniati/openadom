package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;

import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;

/**
 * Pour un référentiel qui n'appartient pas à un référentiel hiérarchique
 */
public record ForNotCompositeReference(String refType) implements HierarchicalKeyFactory {

    /**
     * On est sur un référentiel qui n'appartient pas à une hiérarchie donc la clé naturelle se suffit à elle-même
     */
    @Override
    public Ltree newHierarchicalKey(final Ltree naturalKey, final DataDatum referenceValues) {
        return naturalKey;
    }

    @Override
    public Ltree newHierarchicalReference(final Ltree reference) {
        return reference;
    }

    @Override
    public HierarchicalNode node() {
        return null;
    }
}
