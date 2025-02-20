package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;

public record DynamicComponentsBuilder(RootBuilder rootBuilder) {

    I18n build(final String componentPath,
               final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder,
               final String key,
               I18n i18n,
               final JsonNode jsonNode) {
        final JsonNode dynamicComponents = jsonNode.findPath(ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS);
        final Iterator<Map.Entry<String, JsonNode>> fields = dynamicComponents.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> dynamicComponentEntry = fields.next();
            final JsonNode componentNodeValue = dynamicComponentEntry.getValue();
            final JsonNode defaultValueNode = componentNodeValue.findPath(ConfigurationSchemaNode.OA_DEFAULT_VALUE);
            final String componentKey = dynamicComponentEntry.getKey();
            final boolean required = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder()
                    .build(
                            i18n,
                            componentKey,
                            required,
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
                                    componentKey
                            ),
                            componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                            key
                    );
            i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
            Multiplicity multiplicity = Optional.ofNullable(checkerDescriptionParsing.result())
                    .map(CheckerDescription::multiplicity)
                    .orElse(Multiplicity.ONE);
            final Parsing<ComputationChecker> defaultValueParsing = rootBuilder
                    .getComputationBuilder()
                    .build(
                            i18n,
                            required, multiplicity,
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
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
                                        ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                                        componentKey,
                                        ConfigurationSchemaNode.OA_COMPUTATION
                                )
                        );
                    }
                }
            }
            final Parsing<String> exportHeaderParsing = rootBuilder
                    .addExportHeaders(
                            key,
                            i18n,
                            dynamicComponentEntry,
                            ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS
                    );
            String exportHeaderName = null;
            if (exportHeaderParsing != null) {
                i18n = exportHeaderParsing.i18n();
                exportHeaderName = exportHeaderParsing.result();
            }
            final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                    componentNodeValue,
                    NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
                            componentKey,
                            ConfigurationSchemaNode.OA_TAGS
                    ),
                    rootBuilder);
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(componentNodeValue);
            final Set<String> columns = rootBuilder
                    .getMapper()
                    .convertValue(Optional.of(componentNodeValue)
                                    .map(component -> component.get(ConfigurationSchemaNode.OA_COMPONENTS))
                                    .orElseGet(NullNode::getInstance),
                            Set.class
                    );
            final String prefix = componentNodeValue.findPath(ConfigurationSchemaNode.OA_HEADER_PREFIX).asText();
            final String reference = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REFERENCE).asText();
            if ("null".equals(reference)) {
                rootBuilder.buildError(ConfigurationException.MISSING_REFERENCE_NAME, Map.of(
                                "allDataNames", rootBuilder.getListDataKeys()),
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
                                componentKey,
                                ConfigurationSchemaNode.OA_REFERENCE
                        )
                );
            } else if (!rootBuilder.getListDataKeys().contains(reference)) {
                rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                "referenceName", reference,
                                "allDataNames", rootBuilder.getListDataKeys()),
                        NodeSchemaValidator.joinPath(
                                componentPath,
                                ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
                                componentKey,
                                ConfigurationSchemaNode.OA_REFERENCE
                        )
                );
            }
            final String referenceColumnToLookForHeader = componentNodeValue
                    .findPath(ConfigurationSchemaNode.OA_REFERENCE_COMPONENT_TO_LOOK_FOR_HEADER)
                    .asText();
            final Set<String> listColumnsNameReference = new TreeSet<>();
            if (rootBuilder.getListDataKeys().contains(reference)) {
                rootBuilder
                        .rootNode
                        .findPath(ConfigurationSchemaNode.OA_DATA)
                        .get(reference)
                        .findPath(ConfigurationSchemaNode.OA_BASIC_COMPONENTS)
                        .fields()
                        .forEachRemaining(nodeEntry -> listColumnsNameReference.add(nodeEntry.getKey()));
                if (!listColumnsNameReference.contains(referenceColumnToLookForHeader)) {
                    rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_COLUMN_TO_LOOK_FOR_HEADER, Map.of(
                                    "referenceName", reference,
                                    "columnNameReference", referenceColumnToLookForHeader,
                                    "listColumnsNameReference", listColumnsNameReference),
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS,
                                    componentKey,
                                    ConfigurationSchemaNode.OA_REFERENCE_COMPONENT_TO_LOOK_FOR_HEADER
                            )
                    );
                }
            }
            componentDescriptionBuilder.put(componentKey, new DynamicComponent(
                    ComponentDescription.ComponentDescriptionType.DynamicComponent,
                    componentKey,
                    defaultValueParsing.result(),
                    exportHeaderName == null ? componentKey : exportHeaderName,
                    rootBuilder().getLangRestrictions(componentPath, componentNodeValue),
                    oaTags,
                    required,
                    mandatory,
                    checkerDescriptionParsing.result(),
                    prefix,
                    reference,
                    referenceColumnToLookForHeader,
                    null
            ));
        }
        return i18n;
    }
}
