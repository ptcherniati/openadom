package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.BuilderNode;
import groovyjarjarantlr4.runtime.tree.Tree;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

record ParentChildRelation(BuilderNode parent, BuilderNode child,
                           Integer order) {
    ParentChildRelation(final BuilderNode parent, final BuilderNode child, final Integer order) {
        final BuilderNode parentNode = new BuilderNode(
                parent.level(),
                parent.nodeName(),
                parent.componentKey(),
                parent.columnToLookUpForRecursive(),
                parent.parent(),
                parent.children(),
                parent.depends(),
                parent.order(),
                parent.isRecursive());
        final BuilderNode childNode = new BuilderNode(
                child.level(),
                child.nodeName(),
                child.componentKey(),
                child.columnToLookUpForRecursive(),
                child.parent(),
                child.children(),
                addDependance(child, parent),
                child.order(),
                child.isRecursive());
        this.parent = parentNode;
        this.child = childNode;
        this.order = Optional.ofNullable(order).orElse(child.order());
    }

    private static Set<String> addDependance(final BuilderNode child, final BuilderNode parent) {
        final Set<String> dependances = new TreeSet<>();
        if (CollectionUtils.isNotEmpty(child.depends())) {
            dependances.addAll(child.depends());
        }
        dependances.add(parent.nodeName());
        return dependances;
    }

    private static Set<String> addChildren(final BuilderNode child, final BuilderNode parent) {
        final Set<String> children = new TreeSet<>();
        if (CollectionUtils.isNotEmpty(parent.children())) {
            children.addAll(parent.children());
        }
        children.add(child.nodeName());
        return children;
    }

    ParentChildRelation setParent() {
        final BuilderNode parentNode = new BuilderNode(
                parent().level(),
                parent().nodeName(),
                parent().componentKey(),
                parent().columnToLookUpForRecursive(),
                parent().parent(),
                addChildren(child(), parent()),
                parent().depends(),
                parent().order(),
                parent().isRecursive());
        final BuilderNode childNode = new BuilderNode(
                child().level(),
                child().nodeName(),
                child().componentKey(),
                child.columnToLookUpForRecursive(),
                parentNode,
                child().children(),
                child().depends(),
                child().order(),
                child().isRecursive());

        return new ParentChildRelation(parentNode, childNode, childNode.order());
    }
}
