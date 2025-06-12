package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public record BuilderNode(
        int level,
        String nodeName,
        String componentKey,
        String columnToLookUpForRecursive,
        BuilderNode parent,
        Set<String> children,
        Set<String> depends,
        Integer order,
        boolean isRecursive) implements WithDepends {
    public BuilderNode(final int level, final String nodeName, final String componentKey, String columnToLookUpForRecursive, final BuilderNode parent, final Set<String> children, final Set<String> depends, final Integer order, final boolean isRecursive) {
        this.level = level;
        this.nodeName = nodeName;
        this.componentKey = componentKey;
        this.columnToLookUpForRecursive = columnToLookUpForRecursive;
        this.parent = parent;
        this.children = children;
        this.depends = depends.stream().filter(depend -> !depend.equals(nodeName)).collect(Collectors.toCollection(TreeSet::new));
        this.order = order;
        this.isRecursive = isRecursive;
    }

    public static List<BuilderNode> getNodeLeaves(final Collection<BuilderNode> builderNodes) {
        return builderNodes.stream()
                .filter(node -> node.parent() != null || node.depends().isEmpty())
                .filter(node -> CollectionUtils.isEmpty(node.children()))
                .toList();
    }

    public BuilderNode withAllDepends(final Collection<? extends WithDepends> nodes) {
        final Set<String> depends = new HashSet<>(depends());
        int level = 0;

        if (CollectionUtils.isEmpty(depends)) {
            return this;
        }

        Set<String> processedDependencies = new HashSet<>(depends);
        List<String> childDepends = new ArrayList<>(depends);
        while (CollectionUtils.isNotEmpty(childDepends)) {
            level++;

            List<String> newChildDepends = childDepends.stream()
                    .map(name -> nodes.stream()
                            .filter(node -> node.nodeName().equals(name))
                            .findFirst()
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .map(WithDepends::depends)
                    .flatMap(Set::stream)
                    .filter(dep -> !processedDependencies.contains(dep)) // Éviter les cycles
                    .toList();

            if (newChildDepends.contains(nodeName())) {
                throw new BadApplicationConfigurationException(
                        nodeName(), ConfigurationException.CYCLIC_DEPENDANCIES
                );
            }
            depends.addAll(newChildDepends);
            processedDependencies.addAll(newChildDepends);
            childDepends = newChildDepends;
        }


        return new BuilderNode(level, nodeName(), componentKey(), columnToLookUpForRecursive(), parent(), children(), depends, order(), isRecursive);
    }


    public BuilderNode withComponentKeyAndRecursive(final String componentKey) {
        return new BuilderNode(
                level(),
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

    public BuilderNode withComponentKeyAndNotRecursive(final String componentKey, boolean isParent) {
        return new BuilderNode(
                level(),
                nodeName(),
                isParent ? componentKey : componentKey(),
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
                level(),
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