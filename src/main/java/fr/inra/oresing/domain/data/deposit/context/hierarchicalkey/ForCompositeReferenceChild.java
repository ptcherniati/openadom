package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.transformation.DataTransformer;
import org.apache.logging.log4j.util.Strings;

/**
 * Pour un référentiel qui appartient à un référentiel hiérarchique mais qui est un enfant (= pas la racine).
 */
public record ForCompositeReferenceChild(HierarchicalNode node,
                                         String refType) implements HierarchicalKeyFactory {

    /**
     * On calcule la clé hiérachique en préfixant la clé naturelle avec la clé hiérarchique du parent.
     */
    @Override
    public Ltree newHierarchicalKey(final Ltree naturalKey, final DataDatum referenceDatum) {
        Ltree parentHierarchicalKey = null;
        if (referenceDatum.contains(new DataColumn(node().node().componentKey()))) {
            final DataColumnValue parentHierarchicalKeyColumnValue = referenceDatum.get(new DataColumn(node().node().componentKey()));
            // TODO - catch erreur si valeur multiple dans la clé ou en parent
            Preconditions.checkState(parentHierarchicalKeyColumnValue instanceof DataColumnSingleValue);
            final String parentHierarchicalKeyAsString = ((DataColumnSingleValue) parentHierarchicalKeyColumnValue).getValue().toString();
            if (Strings.isEmpty(parentHierarchicalKeyAsString)) {
                return naturalKey;
            }
            parentHierarchicalKey = DataTransformer.getHierarchicalNodeFromNatural(parentHierarchicalKeyAsString, node().node().parent());
        }

        return parentHierarchicalKey == null ? naturalKey : Ltree.join(parentHierarchicalKey, naturalKey);
    }

    @Override
    public Ltree newHierarchicalReference(final Ltree reference) {
        return Ltree.join(Ltree.fromUnescapedString(node.node().parent()), reference);
    }
}