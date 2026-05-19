package fr.inra.oresing.domain.application.configuration;

import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Node(
        Integer level,
        String nodeName,
        String componentKey,
        String columnToLookUpForRecursive,
        String parent,
        SortedSet<Node> children,
        Set<String> depends,
        Integer order,
        boolean isRecursive
) implements Comparable<Node> {
    public Node {
        if (level == null) {
            level = 0;
        }
    }

    public static SortedSet<Node> buildNode(final Collection<BuilderNode> nodes, final Validation validation) {
        Map<String, BuilderNode> nodesWithAllDepends = nodes.stream()
                .map(node -> node.withAllDepends(nodes))
                .collect(Collectors.toMap(BuilderNode::nodeName, Function.identity()));
        // Construire d'abord les feuilles (nœuds sans enfants dans BuilderNode)
        Map<String, Node> buildedByName = BuilderNode.getNodeLeaves(nodesWithAllDepends.values()).stream()
                .map(node -> new Node(
                        node.level(),
                        node.nodeName(),
                        node.componentKey(),
                        node.columnToLookUpForRecursive(),
                        Optional.of(node).map(BuilderNode::parent).map(BuilderNode::nodeName).orElse(null),
                        new TreeSet<>(),
                        node.depends(),
                        node.order(),
                        node.isRecursive()))
                .collect(Collectors.toMap(Node::nodeName, Function.identity()));
        Map<String, BuilderNode> remainingBuilderNodes = nodesWithAllDepends.entrySet().stream()
                .filter(e -> !buildedByName.containsKey(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return buildNodesRecursively(buildedByName, remainingBuilderNodes);
    }

    /**
     * Construit l'arbre de nœuds récursivement sans muter les objets existants.
     * Les nœuds parents sont reconstruits avec leurs enfants (immuabilité respectée).
     *
     * @param buildedByName     map nom→nœud des nœuds déjà construits (feuilles au départ)
     * @param remainingBuilders nœuds BuilderNode non encore créés comme Node
     */
    private static SortedSet<Node> buildNodesRecursively(
            Map<String, Node> buildedByName,
            Map<String, BuilderNode> remainingBuilders) {

        Map<Boolean, List<Node>> nodesByNoParent = buildedByName.values().stream()
                .collect(Collectors.partitioningBy(Node::isRoot));
        SortedSet<Node> rootNodes = new TreeSet<>(nodesByNoParent.get(true));
        Map<String, List<Node>> nodeWithParent = nodesByNoParent.get(false).stream()
                .collect(Collectors.groupingBy(Node::parent));

        Function<BuilderNode, Node> toNode = bn -> new Node(
                bn.level(), bn.nodeName(), bn.componentKey(), bn.columnToLookUpForRecursive(),
                (bn.parent() != null ? bn.parent().nodeName() : null),
                new TreeSet<>(), bn.depends(), bn.order(), bn.isRecursive());

        while (!nodeWithParent.isEmpty()) {
            Set<Node> parentNodes = new HashSet<>();
            for (Map.Entry<String, List<Node>> nodeEntry : nodeWithParent.entrySet()) {
                String parentName = nodeEntry.getKey();

                // Trouver le nœud parent existant ou le créer depuis remainingBuilders
                Node parent = buildedByName.get(parentName);
                if (parent == null) {
                    BuilderNode parentBuilder = remainingBuilders.get(parentName);
                    if (parentBuilder == null) {
                        throw new IllegalArgumentException("Nœud parent introuvable : " + parentName);
                    }
                    parent = toNode.apply(parentBuilder);
                }

                // Reconstruire le parent avec ses enfants (pas de mutation)
                // On remplace les enfants existants ayant le même nodeName pour éviter les doublons
                SortedSet<Node> mergedChildren = new TreeSet<>(parent.children());
                for (Node newChild : nodeEntry.getValue()) {
                    mergedChildren.removeIf(c -> c.nodeName().equals(newChild.nodeName()));
                    mergedChildren.add(newChild);
                }
                Node updatedParent = new Node(
                        parent.level(), parent.nodeName(), parent.componentKey(),
                        parent.columnToLookUpForRecursive(), parent.parent(),
                        mergedChildren, parent.depends(), parent.order(), parent.isRecursive());

                buildedByName.put(parentName, updatedParent);
                remainingBuilders = remainingBuilders.entrySet().stream()
                        .filter(e -> !e.getKey().equals(parentName))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                if (updatedParent.isRoot()) {
                    // Supprimer l'ancienne version du même nœud racine avant d'ajouter la nouvelle
                    rootNodes.removeIf(n -> n.nodeName().equals(updatedParent.nodeName()));
                    rootNodes.add(updatedParent);
                } else {
                    parentNodes.add(updatedParent);
                }
            }
            nodesByNoParent = parentNodes.stream()
                    .collect(Collectors.partitioningBy(Node::isRoot));
            nodeWithParent = nodesByNoParent.get(false).stream()
                    .collect(Collectors.groupingBy(Node::parent));
        }
        // Ajouter les nœuds restants de remainingBuilders (racines sans enfants)
        remainingBuilders.values().stream()
                .map(toNode)
                .forEach(rootNodes::add);
        return rootNodes;
    }



    private boolean isRoot() {
        return parent()==null || parent().isEmpty();
    }

    @Override
    public int compareTo(final Node o) {
        if (o == null) {
            return 1;
        }
        Integer thisLevel = level();
        Integer oLevel = o.level();
        SortedSet<Node> thisChildren = children();
        SortedSet<Node> oChildren = o.children();

        int compareDeepLevel = deepLevel(thisLevel, thisChildren)
                .compareTo(o.deepLevel(oLevel, oChildren));
        if (compareDeepLevel != 0) {
            return compareDeepLevel;
        }
        int compareLevel = thisLevel.compareTo(oLevel);
        if (compareLevel != 0) {
            return compareLevel;
        }
        String thisName = nodeName();
        String oName = o.nodeName();
        if (depends() != null && oName != null && depends().contains(oName)) return 1;
        if (o.depends() != null && thisName != null && o.depends().contains(thisName)) return -1;
        int compareOrder = Optional.ofNullable(order()).orElse(9999)
                .compareTo(Optional.ofNullable(o.order()).orElse(9999));
        if (compareOrder != 0) {
            return compareOrder;
        }
        if (thisName == null && oName == null) return 0;
        if (thisName == null) return -1;
        if (oName == null) return 1;
        return thisName.compareTo(oName);
    }


    private Integer deepLevel(int deepLevel, SortedSet<Node> childrenLevel) {
        if (CollectionUtils.isEmpty(children())) {
            return deepLevel;
        }
        return childrenLevel.stream()
                .map(child -> deepLevel(child.level(), child.children()))
                .max(Integer::compareTo)
                .orElse(deepLevel);
    }

    public Node findNode(final String refType) {
        if (nodeName().equals(refType)) {
            return this;
        }
        return children.stream()
                .map(child -> child.findNode(refType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

}