package fr.inra.oresing.domain.application.configuration;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public record BuilderNode(
        String nodeName,
        String componentKey,
        String columnToLookUpForRecursive,
        BuilderNode parent,
        List<String> children,
        List<String> depends,
        Integer order,
        boolean isRecursive) implements WithDepends {
    public BuilderNode(final String nodeName, final String componentKey, String columnToLookUpForRecursive, final BuilderNode parent, final List<String> children, final List<String> depends, final Integer order, final boolean isRecursive) {
        this.nodeName = nodeName;
        this.componentKey = componentKey;
        this.columnToLookUpForRecursive = columnToLookUpForRecursive;
        this.parent = parent;
        this.children = children;
        this.depends = depends.stream().filter(depend->!depend.equals(nodeName)).collect(Collectors.toCollection(ArrayList::new));
        this.order = order;
        this.isRecursive = isRecursive;
    }

    public static List<BuilderNode> getNodeLeaves(final Collection<BuilderNode> builderNodes) {
        return builderNodes.stream()
                .filter(node -> node.parent() != null || node.depends().isEmpty() )
                .filter(node -> CollectionUtils.isEmpty(node.children()))
                .toList();
    }

    public BuilderNode withAllDepends(final Collection<? extends WithDepends> nodes) {
        final List<String> depends = depends();
        if (CollectionUtils.isEmpty(depends)) {
            return this;
        }
        List<String> childDepends = depends();
        while (CollectionUtils.isNotEmpty(childDepends)) {
            childDepends = childDepends.stream()
                    .map(name -> nodes.stream().filter(node -> node.nodeName().equals(name)).findFirst().orElse(null))
                    .map(WithDepends::depends)
                    .flatMap(List::stream)
                    .toList();
            depends.addAll(childDepends);
        }
        return new BuilderNode(nodeName(), componentKey(), columnToLookUpForRecursive(), parent(), children(), depends, order(), isRecursive);
    }

    public BuilderNode withComponentKeyAndRecursive(final String componentKey) {
        return new BuilderNode(
                nodeName(),
                componentKey,
                componentKey,
                parent(),
                children(),
                depends(),
                order(),
                true
        );
    }

    public BuilderNode withComponentKeyAndNotRecursive(final String componentKey, Boolean isParent) {
        return new BuilderNode(
                nodeName(),
                isParent?componentKey:componentKey(),
                columnToLookUpForRecursive(),
                parent(),
                children(),
                depends(),
                order(),
                false
        );
    }

    public BuilderNode withComponentKey(String componentKey) {
        return new BuilderNode(
                nodeName(),
                componentKey,
                columnToLookUpForRecursive(),
                parent(),
                children(),
                depends(),
                order(),
                isRecursive()
        );
    }
}
