package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataDatum;

import java.util.Optional;

/**
 * Contrat permettant de créer pour chaque ligne de référentiel sa clé hiérarchique.
 * <p>
 * Comme la création de cette clé dépend de l'appartenance ou non à un référentiel hiérarchique, on gère
 * ça par héritage.
 */

sealed public interface HierarchicalKeyFactory permits ForCompositeReferenceChild, ForCompositeReferenceRoot, ForNotCompositeReference {


    static HierarchicalKeyFactory build(final Application application, final String refType) {
        final Optional<HierarchicalNode> node = application.getConfiguration().findCompositeReferencesUsing(refType);
        final HierarchicalKeyFactory hierarchicalKeyFactory;
        if (node.isPresent()) {
            final HierarchicalNode compositeReferenceDescription = node.get();
            final boolean root = compositeReferenceDescription.node().parent() == null;
            if (root) {
                hierarchicalKeyFactory = new ForCompositeReferenceRoot(compositeReferenceDescription, refType);
            } else {
                final DataColumn parentHierarchicalKeyColumn = new DataColumn(compositeReferenceDescription.node().componentKey());
                hierarchicalKeyFactory = new ForCompositeReferenceChild(compositeReferenceDescription, refType);
            }
        } else {
            hierarchicalKeyFactory = new ForNotCompositeReference(refType);
        }
        return hierarchicalKeyFactory;
    }

    Ltree newHierarchicalKey(Ltree naturalKey, DataDatum referenceValues);

    Ltree newHierarchicalReference(Ltree reference);

    HierarchicalNode node();

    default String parent() {
        return Optional.ofNullable(node()).map(HierarchicalNode::node).map(Node::parent).orElse("");
    }

}
