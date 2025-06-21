package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.*;

public record PatternComponentsBuilder(RootBuilder rootBuilder) {

    I18n build(final String componentPath, final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder, final String dataKey, I18n i18n, final JsonNode jsonNode) {
        final JsonNode validations = jsonNode
                .findPath(ConfigurationSchemaNode.OA_PATTERN_COMPONENTS);
        final Iterator<Map.Entry<String, JsonNode>> fields = validations.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> patternComponentEntry = fields.next();
            final String componentKey = patternComponentEntry.getKey();
            final JsonNode patternComponentNode = patternComponentEntry.getValue();
            final JsonNode defaultValueNode = patternComponentNode.findPath(ConfigurationSchemaNode.OA_DEFAULT_VALUE);
            final boolean required = patternComponentNode.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);

            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder().build(
                    i18n,
                    componentKey,
                    required,
                    "%1$s.OA_patternComponents.%2$s".formatted(componentPath, componentKey),
                    patternComponentNode.get(ConfigurationSchemaNode.OA_CHECKER),
                    dataKey);
            i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
            Multiplicity multiplicity = Optional.ofNullable(checkerDescriptionParsing.result())
                    .map(CheckerDescription::multiplicity)
                    .orElse(Multiplicity.ONE);
            Parsing<ComputationChecker> defaultValueParsing;
            if (defaultValueNode != null && !defaultValueNode.isMissingNode()) {
                defaultValueParsing = rootBuilder.getComputationBuilder().build(
                        i18n,
                        required, multiplicity,
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                ConfigurationSchemaNode.OA_PATTERN_COMPONENTS,
                                componentKey,
                                ConfigurationSchemaNode.OA_DEFAULT_VALUE),
                        defaultValueNode);
                i18n = defaultValueParsing.i18n();
            } else {
                defaultValueParsing = new Parsing<>(i18n, null);
            }
            final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(dataKey, i18n, patternComponentEntry, ConfigurationSchemaNode.OA_PATTERN_COMPONENTS);
            String exportHeaderName = null;
            if (exportHeaderParsing != null) {
                i18n = exportHeaderParsing.i18n();
                exportHeaderName = exportHeaderParsing.result();
            }
            final String patternForComponents = patternComponentNode.findPath(ConfigurationSchemaNode.OA_PATTERN_FOR_COMPONENTS).asText((""));
            //TODO -> à voir avec un exemple à faire
            final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                    patternComponentNode,
                    NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_PATTERN_COMPONENTS,
                            componentKey,
                            ConfigurationSchemaNode.OA_TAGS),
                    rootBuilder);
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(patternComponentNode);
            final Parsing<Map<String, PatternComponentQualifiers>> patternComponentsQualifiersParsing = rootBuilder().getPatternComponentQualifiersBuilder()
                    .build(
                            dataKey,
                            componentPath,
                            componentKey,
                            componentDescriptionBuilder,
                            i18n,
                            patternComponentNode
                    );
            final Parsing<Map<String, PatternComponentAdjacents>> patternComponentsAdjacentsParsing = rootBuilder().getPatternComponentAdjacentsBuilder()
                    .build(
                            dataKey,
                            componentPath,
                            componentKey,
                            componentDescriptionBuilder,
                            i18n,
                            patternComponentNode
                    );
            i18n = patternComponentsQualifiersParsing.i18n();
            componentDescriptionBuilder.put(
                    componentKey,
                    new PatternComponent(
                            ComponentDescription.ComponentDescriptionType.PatternComponent,
                            componentKey,
                            defaultValueParsing.result(),
                            exportHeaderName == null ? componentKey : exportHeaderName,
                            rootBuilder().getLangRestrictions(componentPath, patternComponentNode),
                            oaTags,
                            required,
                            mandatory,
                            checkerDescriptionParsing.result(),
                            patternForComponents,
                            patternComponentsQualifiersParsing.result(),
                            patternComponentsAdjacentsParsing.result(),
                            null
                    ));
        }
        return i18n;
    }
}