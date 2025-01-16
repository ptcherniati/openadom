package fr.inra.oresing.domain.application.configuration;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record Node(
        String nodeName,
        String componentKey,
        String columnToLookUpForRecursive,
        String parent,
        SortedSet<Node> children,
        List<String> depends,
        Integer order,
        boolean isRecursive
) implements Comparable<Node> {

    public static SortedSet<Node> buildNode(final Collection<BuilderNode> nodes, final Validation validation) {
        record Builder(Collection<BuilderNode> fromNodes, Set<Node> buildedNodes, Set<Node> currentParentsNodes,
                       Validation validation) {
            static SortedSet<Node> build(final Collection<BuilderNode> fromNodes, final Validation validation) {
                Map<String, BuilderNode> nodesWithAllDepends = fromNodes.stream()
                        .map(node -> node.withAllDepends(fromNodes))
                        .collect(Collectors.toMap(BuilderNode::nodeName, Function.identity()));
                final Set<Node> buildedNodes = BuilderNode.getNodeLeaves(nodesWithAllDepends.values()).stream()
                        .map(node -> new Node(
                                node.nodeName(),
                                node.componentKey(),
                                node.columnToLookUpForRecursive(),
                                Optional.of(node).map(BuilderNode::parent).map(BuilderNode::nodeName).orElse(null),
                                new TreeSet<>(),
                                node.depends(),
                                node.order(),
                                node.isRecursive())
                        )
                        .collect(Collectors.toCollection(TreeSet::new));
                Map<String, BuilderNode> notBuildedNodes = nodesWithAllDepends.entrySet().stream()
                        .filter(node -> buildedNodes.stream()
                                .noneMatch(bn -> bn.nodeName().equals(node.getValue().nodeName())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
                        );
                return buildNodesRecursively(buildedNodes, notBuildedNodes.values());
            }
            private static SortedSet<Node> buildNodesRecursively(Set<Node> buildedNodes, Collection<BuilderNode> notBuildedNodes) {
                Map<Boolean, List<Node>> nodesByNoParent = buildedNodes.stream()
                        .collect(Collectors.partitioningBy(Node::isRoot));
                SortedSet<Node> rootNodes = new TreeSet<>(nodesByNoParent.get(true));
                Map<String, List<Node>> nodeWithParent = nodesByNoParent.get(false).stream()
                        .collect(Collectors.groupingBy(Node::parent));
                Function<BuilderNode, Node> findNode = node -> new Node(
                        node.nodeName(),
                        node.componentKey(),
                        node.columnToLookUpForRecursive(),
                        Optional.of(node)
                                .map(BuilderNode::parent)
                                .map(BuilderNode::nodeName)
                                .orElse(null),
                        new TreeSet<>(),
                        node.depends(),
                        node.order(),
                        node.isRecursive());
                while (!nodeWithParent.isEmpty()) {
                    Set<Node> parentNodes = new HashSet<>();
                    for (Map.Entry<String, List<Node>> nodeEntry : nodeWithParent.entrySet()) {
                        String parentName = nodeEntry.getKey();
                        Node parent = buildedNodes.stream()
                                .filter(node -> node.nodeName().equals(parentName))
                                .findFirst()
                                .orElse(null);
                        if(parent ==null) {
                            parent = notBuildedNodes.stream()
                                    .filter(node -> node.nodeName().equals(parentName))
                                    .map(findNode)
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException(parentName));
                        }
                        Node finalParent = parent;
                        nodeEntry.getValue()
                                .forEach(child -> finalParent.children().add(child));
                        notBuildedNodes = notBuildedNodes.stream()
                                .filter(node -> !node.nodeName().equals(parentName))
                                .toList();
                        buildedNodes.add(parent);
                        if (parent.isRoot()) {
                            rootNodes.add(parent);
                        } else {
                            parentNodes.add(parent);
                        }

                    }
                    nodesByNoParent = parentNodes.stream()
                        .collect(Collectors.partitioningBy(Node::isRoot));

                    nodeWithParent = nodesByNoParent.get(false).stream()
                            .collect(Collectors.groupingBy(Node::parent));
                }
                notBuildedNodes.stream()
                                .map(findNode)
                                        .forEach(rootNodes::add);
                return rootNodes;
            }

            private Node findNode(final String nodeName) {
                return buildedNodes().stream().filter(node -> node.nodeName().equals(nodeName)).findFirst().orElse(null);
            }
            private Node findOrCreateNode(final String childNodeName) {
                final Node childNode = findNode(childNodeName);
                if (childNode != null) {
                    return childNode;
                }
                final BuilderNode builderNode = fromNodes().stream()
                        .filter(node -> node.nodeName().equals(childNodeName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("pas ici"));
                return new Node(
                        builderNode.nodeName(),
                        builderNode.componentKey(),
                        builderNode.columnToLookUpForRecursive(),
                        Optional.of(builderNode)
                                .map(BuilderNode::parent)
                                .map(BuilderNode::nodeName)
                                .orElse(null),
                        new TreeSet<>(),
                        builderNode.depends(),
                        builderNode.order(),
                        builderNode.isRecursive());
            }
        }
        return Builder.build(nodes, validation);
    }



    private boolean isRoot() {
        return Optional.ofNullable(parent()).map(String::isEmpty).orElse(true);
    }
    private List<String> dependsRecursively(){
        Set<String> result = new HashSet<>(depends());
        children().forEach(child -> result.addAll(child.dependsRecursively()));
        return new ArrayList<>(result);
    }

    @Override
    public int compareTo(final Node o) {
        if (o == null) {
            return 1;
        }
        if (o.dependsRecursively().contains(nodeName()) ) {
            return -o.dependsRecursively().size();
        }
        if (dependsRecursively().contains(o.nodeName())) {
            return dependsRecursively().size();
        }
        int compareOrder = Optional.ofNullable(order()).orElse(9999).compareTo(Optional.ofNullable(o.order()).orElse(9999));
        if(compareOrder == 0){
            return nodeName().compareTo(o.nodeName());
        }
        return compareOrder;
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
