package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.PatternComponentAdjacents;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;

public record PatternComponentAdjacentsBuilder(RootBuilder rootBuilder) {
    Parsing<Map<String, PatternComponentAdjacents>> build(
            final String dataKey,
            final String componentPath,
            final String componentKey,
            final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder,
            I18n i18n,
            final JsonNode patternComponentNode
    ) {
        final ArrayNode componentsArrayNode = Optional.ofNullable(patternComponentNode)
                .map(node -> node.get(ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS))
                .map(ArrayNode.class::cast)
                .orElse(new ArrayNode(JsonNodeFactory.instance));
        if (!componentsArrayNode.isEmpty()) {
            int componentNumber = 0;
            final ImmutableMap.Builder<String, PatternComponentAdjacents> patternColumnComponentBuilder = new ImmutableMap.Builder<>();
            for (final JsonNode node : componentsArrayNode) {
                ++componentNumber;
                final Map.Entry<String, JsonNode> patternColumnComponentNode = node.fields().next();
                final String label = patternColumnComponentNode.getKey();
                final JsonNode componentNodeValue = patternColumnComponentNode.getValue();
                final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                        componentNodeValue,
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS,
                                componentKey,
                                label,
                                ConfigurationSchemaNode.OA_TAGS),
                        rootBuilder);
                final boolean required = node.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
                final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(node);
                final String importHeaderPattern = node
                        .findPath(ConfigurationSchemaNode.OA_IMPORT_HEADER_PATTERN)
                        .asText("");
                if (Strings.isNullOrEmpty(importHeaderPattern)) {
                    rootBuilder().buildError(
                            ConfigurationException.MISSING_IMPORT_HEADER_PATTERN ,
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS,
                                    componentKey,
                                    label,
                                    ConfigurationSchemaNode.OA_IMPORT_HEADER_PATTERN)
                    );
                }

                AbstractMap.SimpleEntry<String, JsonNode> adjacentEntry = new AbstractMap.SimpleEntry<>(Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentKey, patternColumnComponentNode.getKey()), patternColumnComponentNode.getValue());
                final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(dataKey, i18n, adjacentEntry, ConfigurationSchemaNode.OA_PATTERN_COMPONENTS);
                if (exportHeaderParsing != null) {
                    i18n = exportHeaderParsing.i18n();
                }
                final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder()
                        .build(
                                i18n,
                                componentKey,
                                false,
                                "%1$s.OA_patternComponents.%2$s.%3$s".formatted(componentPath, componentKey, label),
                                componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                                label);
                i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
                final PatternComponentAdjacents patternColumnComponent = new PatternComponentAdjacents(
                        ComponentDescription.ComponentDescriptionType.PatternComponentAdjacents,
                        label,
                        importHeaderPattern,
                        oaTags,
                        Objects.requireNonNull(exportHeaderParsing).result(),
                        required,
                        mandatory,
                        rootBuilder().getLangRestrictions(componentPath, componentNodeValue),
                        checkerDescriptionParsing.result()
                );
                patternColumnComponentBuilder.put(label, patternColumnComponent);
                componentDescriptionBuilder.put(
                        Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentKey,label),
                        patternColumnComponent
                );
            }
            return new Parsing<>(i18n, patternColumnComponentBuilder.build());
        }
        return new Parsing<>(i18n, Map.of());
    }
}