package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.DateChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public record AuthorizationBuilder(RootBuilder rootBuilder) {

    Authorization build(final String path, final String dataKey, JsonNode dataNode, ImmutableMap<String, ComponentDescription> componentDescriptions, Map<CheckerDescription.CheckerDescriptionType, List<String>> componentValidationsByType) {
        JsonNode authorizationNode = dataNode.get(ConfigurationSchemaNode.OA_AUTHORIZATION);
        if (authorizationNode == null) {
            return null;
        }
        List<AuthorizationScopeComponentData> referenceComponents = componentDescriptions.entrySet()
                .stream()
                .filter(entry -> Optional.ofNullable(entry.getValue())
                        .map(ComponentDescription::checker)
                        .filter(ReferenceChecker.class::isInstance)
                        .isPresent()
                )
                .map(entry->new AuthorizationScopeComponentData(entry.getKey(), ((ReferenceChecker)entry.getValue().checker()).refType()))
                .collect(Collectors.toCollection(ArrayList::new));
        //referenceComponents.addAll(componentValidationsByType.getOrDefault(CheckerDescription.CheckerDescriptionType.ReferenceChecker, List.of()));
        List<String> dateComponents = componentDescriptions.entrySet()
                .stream()
                .filter(entry -> Optional.ofNullable(entry.getValue())
                        .map(ComponentDescription::checker)
                        .filter(DateChecker.class::isInstance)
                        .isPresent()
                )
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
        dateComponents.addAll(componentValidationsByType.getOrDefault(CheckerDescription.CheckerDescriptionType.DateChecker, List.of()));

        Function<Iterator<JsonNode>, List<AuthorizationScopeComponentData>> resolveComponentsAsReferenceComponent = iterator -> resolveComponentsAsReference(iterator, dataKey, path, referenceComponents);
        Function<String, String> resolveComponentsAsDateComponent = dateComponentName -> resolveComponentsAsDateComponent(dateComponentName, dataKey, path, dateComponents);
        List<AuthorizationScopeComponentData> authorizationScope = Optional.of(authorizationNode)
                .map(node -> node.findPath(ConfigurationSchemaNode.OA_AUTHORIZATION_SCOPES))
                .map(JsonNode::elements)
                .map(resolveComponentsAsReferenceComponent)
                .orElse(null);
        String timescope = Optional.ofNullable(authorizationNode)
                .map(node -> node.findPath(ConfigurationSchemaNode.OA_TIME_SCOPE))
                .map(JsonNode::asText)
                .map(resolveComponentsAsDateComponent)
                .orElse(null);

        return new Authorization(
                authorizationScope,
                timescope
        );
    }

    private String resolveComponentsAsDateComponent(String dateComponentName, String dataKey, String path, List<String> dateComponents) {
        if (dateComponents.contains(dateComponentName)) {
            return dateComponentName;
        }
        rootBuilder().buildError(
                ConfigurationException.INVALID_COMPONENT_DATE_FOR_TIMESCOPE_COMPONENT_NAME,
                Map.of(
                        "componentName", dateComponentName,
                        "knownDateComponents", dateComponents
                ),
                NodeSchemaValidator.joinPath(
                        path,
                        dataKey,
                        ConfigurationSchemaNode.OA_AUTHORIZATION,
                        ConfigurationSchemaNode.OA_AUTHORIZATION_SCOPES
                )
        );
        return null;
    }

    private List<AuthorizationScopeComponentData> resolveComponentsAsReference(Iterator<JsonNode> jsonNodeIterator, String dataKey, String path, List<AuthorizationScopeComponentData> referenceComponents) {
        Predicate<String> isComponentAReference = componentName -> isComponentAReference(componentName, dataKey, path, referenceComponents);
        Map<Boolean, List<String>> authorizationScopeComponent = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(jsonNodeIterator, Spliterator.IMMUTABLE),
                        false
                )
                .map(JsonNode::asText)
                .collect(Collectors.partitioningBy(isComponentAReference));
        return authorizationScopeComponent.get(true).stream()
                .map(component->new AuthorizationScopeComponentData(component, referenceComponents.stream().filter(authorizationScopeComponentData -> authorizationScopeComponentData.component().equals(component)).map(AuthorizationScopeComponentData::data).findFirst().orElse(null)))
                .toList();
    }

    private boolean isComponentAReference(String componentName, String dataKey, String path, List<AuthorizationScopeComponentData> referenceComponents) {
        boolean isDefinedComponent = referenceComponents.stream().anyMatch(authorizationScopeComponentData -> authorizationScopeComponentData.component().equals(componentName));
        if (!isDefinedComponent) {
            rootBuilder().buildError(
                    ConfigurationException.INVALID_COMPONENT_REFERENCE_FOR_AUTHORIZATION_SCOPE_COMPONENT_NAME,
                    Map.of(
                            "componentName", componentName,
                            "knownReferenceComponents", referenceComponents
                    ),
                    NodeSchemaValidator.joinPath(
                            path,
                            dataKey,
                            ConfigurationSchemaNode.OA_AUTHORIZATION,
                            ConfigurationSchemaNode.OA_AUTHORIZATION_SCOPES
                    )
            );
            return false;
        }
        return true;
    }
}