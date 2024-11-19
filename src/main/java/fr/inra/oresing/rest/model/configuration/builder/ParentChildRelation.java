package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.BuilderNode;
import org.apache.commons.collections4.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

record ParentChildRelation(BuilderNode parent, BuilderNode child,
                           Integer order) {
    ParentChildRelation(final BuilderNode parent, final BuilderNode child, final Integer order) {
        final BuilderNode parentNode = new BuilderNode(
                parent.nodeName(),
                parent.componentKey(),
                parent.columnToLookUpForRecursive(),
                parent.parent(),
                parent.children(),
                parent.depends(),
                parent.order(),
                parent.isRecursive());
        final BuilderNode childNode = new BuilderNode(
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

    private static List<String> addDependance(final BuilderNode child, final BuilderNode parent) {
        final List<String> dependances = new LinkedList<>();
        if (CollectionUtils.isNotEmpty(child.depends())) {
            dependances.addAll(child.depends());
        }
        if (!dependances.contains(parent.nodeName())) {
            dependances.add(parent.nodeName());
        }
        return dependances;
    }

    private static List<String> addChildren(final BuilderNode child, final BuilderNode parent) {
        final List<String> children = new LinkedList<>();
        if (CollectionUtils.isNotEmpty(parent.children())) {
            children.addAll(parent.children());
        }
        if (!children.contains(child.nodeName())) {
            children.add(child.nodeName());
        }
        return children;
    }

    ParentChildRelation setParent() {
        final BuilderNode parentNode = new BuilderNode(
                parent().nodeName(),
                parent().componentKey(),
                parent().columnToLookUpForRecursive(),
                parent().parent(),
                addChildren(child(), parent()),
                parent().depends(),
                parent().order(),
                parent().isRecursive());
        final BuilderNode childNode = new BuilderNode(
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
