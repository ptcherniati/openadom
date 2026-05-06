package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.BuilderNode;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Construit le graphe de dépendances entre référentiels à partir d'une liste
 * de {@link ReferenceChecker} par type de données.
 *
 * <p>Ce composant extrait la logique de détection des relations (récursivité,
 * parentalité, dépendance simple) qui était auparavant mélangée dans
 * {@link HierarchicalDependancesBuilder#buildHierchicalDependances}.</p>
 *
 * <p>Le résultat est une map de {@link BuilderNode} prête à être passée à
 * {@link HierarchicalDependancesBuilder#buildHierchicalDependances}.</p>
 */
public final class ReferenceGraphBuilder {

    private ReferenceGraphBuilder() {
        // Classe utilitaire — pas d'instantiation
    }

    /**
     * Détermine le type de relation entre deux nœuds du graphe.
     */
    public enum RelationType {
        /** A référence B de façon ordinaire : A dépend de B (B doit être chargé avant A) */
        DEPENDS,
        /** A se référence lui-même : A est récursif */
        RECURSIVE,
        /** B est le parent hiérarchique de A : A est enfant de B */
        PARENT_CHILD
    }

    /**
     * Décrit une relation typée entre un nœud source et un nœud cible.
     *
     * @param sourceDataName nom du nœud source (le référentiel qui porte le checker)
     * @param targetRefType  nom du nœud cible (le référentiel référencé)
     * @param componentKey   composant portant la clé de référence
     * @param relationType   type de la relation
     */
    public record ReferenceRelation(
            String sourceDataName,
            String targetRefType,
            String componentKey,
            RelationType relationType) {
    }

    /**
     * Analyse les checkers par nœud et retourne la liste des relations détectées.
     *
     * @param checkersByDataName map dataName → liste de {@link ReferenceChecker}
     * @return liste des relations détectées
     */
    public static List<ReferenceRelation> detectRelations(
            Map<String, List<ReferenceChecker>> checkersByDataName) {

        List<ReferenceRelation> relations = new ArrayList<>();
        for (Map.Entry<String, List<ReferenceChecker>> entry : checkersByDataName.entrySet()) {
            String dataName = entry.getKey();
            for (ReferenceChecker checker : entry.getValue()) {
                String refType      = checker.refType();
                String componentKey = checker.componentKey();
                boolean isRecursive = checker.isRecursive();
                boolean isParent    = checker.isParent();

                if (dataName.equals(refType) || isRecursive) {
                    relations.add(new ReferenceRelation(dataName, refType, componentKey, RelationType.RECURSIVE));
                } else if (isParent) {
                    relations.add(new ReferenceRelation(dataName, refType, componentKey, RelationType.PARENT_CHILD));
                } else {
                    relations.add(new ReferenceRelation(dataName, refType, componentKey, RelationType.DEPENDS));
                }
            }
        }
        return Collections.unmodifiableList(relations);
    }

    /**
     * Construit la map de {@link BuilderNode} à partir des relations et des ordres.
     *
     * <p>Délègue la construction effective à
     * {@link HierarchicalDependancesBuilder#buildHierchicalDependances} en passant
     * les nœuds initiaux.</p>
     *
     * @param checkersByDataName map dataName → checkers
     * @param ordersByDataName   map dataName → ordre IHM
     * @return map nom→BuilderNode avec les dépendances et parentalités renseignées
     */
    public static Map<String, BuilderNode> buildNodes(
            Map<String, List<ReferenceChecker>> checkersByDataName,
            Map<String, Integer> ordersByDataName) {

        Map<String, BuilderNode> nodes = ordersByDataName.keySet().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> new BuilderNode(0, name, null, null, null,
                                new TreeSet<>(), new TreeSet<>(), ordersByDataName.get(name), false)
                ));

        for (ReferenceRelation rel : detectRelations(checkersByDataName)) {
            BuilderNode existing = nodes.get(rel.sourceDataName());
            BuilderNode target   = nodes.get(rel.targetRefType());

            switch (rel.relationType()) {
                case RECURSIVE -> {
                    if (existing != null) {
                        nodes.put(rel.sourceDataName(),
                                existing.withComponentKeyAndRecursive(rel.componentKey()));
                    }
                }
                case DEPENDS -> {
                    if (existing != null && target != null) {
                        ParentChildRelation relation = new ParentChildRelation(target, existing, ordersByDataName.get(rel.sourceDataName()));
                        nodes.put(relation.parent().nodeName(), relation.parent());
                        nodes.put(relation.child().nodeName(),  relation.child());
                    }
                }
                case PARENT_CHILD -> {
                    if (existing != null && target != null) {
                        ParentChildRelation relation = new ParentChildRelation(target, existing, ordersByDataName.get(rel.sourceDataName()));
                        relation = relation.setParent();
                        nodes.put(relation.parent().nodeName(), relation.parent());
                        nodes.put(relation.child().nodeName(),  relation.child());
                    }
                }
            }
        }
        return Collections.unmodifiableMap(nodes);
    }
}