package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;

import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;

/**
 * Pour un référentiel qui est la racine d'un référentiel hiérarchique
 */
public record ForCompositeReferenceRoot(HierarchicalNode node,
                                        String refType) implements HierarchicalKeyFactory {

    /**
     * On est sur un référentiel qui est à la racine de la hiérarchie donc sa clé hiérarchique est simplement sa clé naturelle (pas de parent)
     */
    @Override
    public Ltree newHierarchicalKey(final Ltree naturalKey, final DataDatum referenceValues) {
        return naturalKey;
    }

    @Override
    public Ltree newHierarchicalReference(final Ltree reference) {
        return reference;
    }
}
