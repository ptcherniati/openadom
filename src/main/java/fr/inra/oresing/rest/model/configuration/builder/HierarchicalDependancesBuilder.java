package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public record HierarchicalDependancesBuilder(
        Map<String, StandardDataDescription> data,
        Map<String, List<ReferenceChecker>> checkerdescriptions,
        Map<String, Integer> orders,
        Set<Tag> domainTags

) {
    public static HierarchicalDependancesBuilder of(
            Map<CheckerDescription.CheckerDescriptionType, Map<String, Map<String, List<CheckerDescription>>>> checkers,
            Map<String, StandardDataDescription> data,
            Set<Tag> domaintags
    ) {

        final Map<String, List<ReferenceChecker>> checkerdescriptions = checkers
                .getOrDefault(CheckerDescription.CheckerDescriptionType.ReferenceChecker, new HashMap<>())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().values().stream()
                                .flatMap(List::stream)
                                .filter(ReferenceChecker.class::isInstance)
                                .map(ReferenceChecker.class::cast)
                                .toList()
                ));

        final Map<String, Integer> orders = data.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getOrder()
                ));
        return new HierarchicalDependancesBuilder(
                data,
                checkerdescriptions,
                orders,
                domaintags
        );
    }

    @SuppressWarnings("java:S1172")
    static SortedSet<Node> buildHierchicalDependances(
            Consumer<ValidationParams> buildErrorWithValidationParams,
            final Map<String, List<ReferenceChecker>> checkers,
            final Map<String, Integer> orderTags,
            final Map<String, BuilderNode> nodes, Set<Tag> domainTags) {

        for (final Map.Entry<String, List<ReferenceChecker>> checkerEntry : checkers.entrySet()) {
            final String dataName = checkerEntry.getKey();
            checkerEntry.getValue()
                    .forEach(checker -> {
                        final Boolean isParent = checker.isParent() && !checker.isRecursive();
                        final String refType = checker.refType();
                        final String componentKey = checker.componentKey();
                        final boolean isRecursive = checker.isRecursive() || (checker.isParent() && dataName.equals(refType));
                        if (dataName.equals(refType)) {
                            Objects.requireNonNull(nodes.put(refType, nodes.containsKey(refType) ?
                                    nodes.get(refType).withComponentKeyAndRecursive(componentKey) :
                                    new BuilderNode(0, refType, componentKey, componentKey, null, new TreeSet<>(), new TreeSet<>(), orderTags.get(refType), isRecursive)
                            )).withComponentKeyAndRecursive(componentKey);
                        } else {
                            ParentChildRelation relation = new ParentChildRelation(
                                    nodes.containsKey(refType) ?
                                            nodes.get(refType) :
                                            new BuilderNode(0, refType, componentKey, null, null, new TreeSet<>(), new TreeSet<>(), orderTags.get(refType), isRecursive).withComponentKeyAndNotRecursive(componentKey, isParent),
                                    nodes.containsKey(dataName) ?
                                            nodes.get(dataName).withComponentKey(componentKey) :
                                            new BuilderNode(0, dataName, componentKey, null, null, new TreeSet<>(), new TreeSet<>(), orderTags.get(dataName), isRecursive),
                                    orderTags.get(dataName)
                            );
                            if (isParent) {
                                relation = relation.setParent();
                            }
                            nodes.put(relation.parent().nodeName(), relation.parent());
                            nodes.put(relation.child().nodeName(), relation.child());
                        }
                    });
        }
        return Node.buildNode(nodes.values());
    }


    public SortedSet<Node> build(Consumer<ValidationParams> buildErrorWithValidationParams) {
        return buildHierchicalDependances(
                buildErrorWithValidationParams,
                checkerdescriptions,
                orders,
                data.keySet().stream()
                        .map(name -> new BuilderNode(0, name, null, null, null, new TreeSet<>(), new TreeSet<>(), orders.get(name), false))
                        .collect(Collectors.toMap(BuilderNode::nodeName, Function.identity())),
                domainTags
        );
    }
}