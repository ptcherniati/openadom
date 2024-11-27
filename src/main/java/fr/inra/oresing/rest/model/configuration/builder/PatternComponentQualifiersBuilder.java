package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.PatternComponentQualifiers;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record PatternComponentQualifiersBuilder(RootBuilder rootBuilder) {
    Parsing<Map<String, PatternComponentQualifiers>> build(
            final String dataKey,
            final String componentPath,
            final String componentKey,
            final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder,
            I18n i18n,
            final JsonNode patternComponentNode
    ) {
        final ArrayNode componentsArrayNode = Optional.ofNullable(patternComponentNode)
                .map(node -> node.findPath(ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS))
                .map(ArrayNode.class::cast)
                .orElse(new ArrayNode(JsonNodeFactory.instance));
        if (!componentsArrayNode.isEmpty()) {
            int componentNumber = 0;
            final ImmutableMap.Builder<String, PatternComponentQualifiers> patternColumnComponentBuilder = new ImmutableMap.Builder<String, PatternComponentQualifiers>();
            for (final JsonNode node : componentsArrayNode) {
                ++componentNumber;
                final Map.Entry<String, JsonNode> patternColumnComponentNode = node.fields().next();
                final String label = patternColumnComponentNode.getKey();
                final JsonNode componentNodeValue = patternColumnComponentNode.getValue();
                final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                        componentNodeValue,
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS,
                                componentKey,
                                label,
                                ConfigurationSchemaNode.OA_TAGS),
                        rootBuilder);

                final boolean required = patternComponentNode.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);

                final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(dataKey, i18n, patternColumnComponentNode, ConfigurationSchemaNode.OA_PATTERN_COMPONENTS);
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
                i18n = checkerDescriptionParsing.i18n();
                final JsonNode defaultValueNode = componentNodeValue.get(ConfigurationSchemaNode.OA_DEFAULT_VALUE);
                final Parsing<ComputationChecker> defaultValueParsing;
                if (defaultValueNode != null) {
                    defaultValueParsing = rootBuilder.getComputationBuilder().build(
                            i18n,
                            required, Multiplicity.ONE,
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_BASIC_COMPONENTS,
                                    componentKey,
                                    ConfigurationSchemaNode.OA_DEFAULT_VALUE
                            ),
                            defaultValueNode
                    );
                    i18n = defaultValueParsing.i18n();
                    if (defaultValueParsing.result().getReferences() != null) {
                        for (final String reference : defaultValueParsing.result().getReferences()) {
                            if (!rootBuilder.getListDataKeys().contains(reference)) {
                                rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                                "referenceName", reference,
                                                "allDataNames", rootBuilder.getListDataKeys()),
                                        NodeSchemaValidator.joinPath(
                                                componentPath,
                                                ConfigurationSchemaNode.OA_BASIC_COMPONENTS,
                                                componentKey,
                                                ConfigurationSchemaNode.OA_DEFAULT_VALUE,
                                                ConfigurationSchemaNode.OA_REFERENCES
                                        ));
                            }
                        }
                    }
                } else {
                    defaultValueParsing = new Parsing<>(i18n, null);
                }
                final PatternComponentQualifiers patternColumnComponent = new PatternComponentQualifiers(
                        ComponentDescription.ComponentDescriptionType.PatternComponentQualifiers,
                        label,
                        defaultValueParsing.result(),
                        oaTags,
                        label,
                        rootBuilder().getLangRestrictions(componentPath, componentNodeValue),
                        componentNumber,
                        checkerDescriptionParsing.result(),
                        null
                );
                patternColumnComponentBuilder.put(label, patternColumnComponent);
                componentDescriptionBuilder.put(
                        Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentKey, label),
                        patternColumnComponent
                );
            }
            return new Parsing<>(i18n, patternColumnComponentBuilder.build());
        }
        return new Parsing<>(i18n, Map.of());
    }
}
