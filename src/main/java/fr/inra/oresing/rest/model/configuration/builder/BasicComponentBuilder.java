package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;

public record BasicComponentBuilder(RootBuilder rootBuilder) {

    I18n build(final String componentPath,
               final ImmutableMap.Builder<String, ComponentDescription> basicComponentDescriptions,
               final String key,
               I18n i18n,
               final JsonNode jsonNode) {
        final JsonNode basicComponents = jsonNode
                .findPath(ConfigurationSchemaNode.OA_BASIC_COMPONENTS);
        final Iterator<Map.Entry<String, JsonNode>> fields = basicComponents.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> componentEntry = fields.next();
            final JsonNode componentNodeValue = componentEntry.getValue();
            final JsonNode defaultValueNode = componentNodeValue.findPath(ConfigurationSchemaNode.OA_DEFAULT_VALUE);
            final String componentKey = componentEntry.getKey();
            final boolean required = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder()
                    .build(i18n,
                            componentKey,
                            required,
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_BASIC_COMPONENTS,
                                    componentKey),
                            componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                            key);

            i18n = checkerDescriptionParsing.i18n();
            Multiplicity multiplicity = Optional.ofNullable(checkerDescriptionParsing.result())
                    .map(CheckerDescription::multiplicity)
                    .orElse(Multiplicity.ONE);
            final Parsing<ComputationChecker> defaultValueParsing = rootBuilder.getComputationBuilder().build(
                    i18n,
                    required, multiplicity,
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
            final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                    componentNodeValue,
                    NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_BASIC_COMPONENTS,
                            componentKey,
                            ConfigurationSchemaNode.OA_TAGS
                    ),
                    rootBuilder);
            final String importHeader;
            if (!componentNodeValue
                    .findPath(ConfigurationSchemaNode.OA_IMPORT_HEADER)
                    .isEmpty()
            ) {
                importHeader = componentNodeValue
                        .get(ConfigurationSchemaNode.OA_IMPORT_HEADER)
                        .findPath(ConfigurationSchemaNode.OA_HEADER_NAME)
                        .asText(componentKey);
            } else {
                importHeader = componentNodeValue
                        .findPath(ConfigurationSchemaNode.OA_IMPORT_HEADER)
                        .asText(componentKey);
            }
            final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(key, i18n, componentEntry, ConfigurationSchemaNode.OA_BASIC_COMPONENTS);
            String exportHeaderName = null;
            if (exportHeaderParsing != null) {
                i18n = exportHeaderParsing.i18n();
                exportHeaderName = exportHeaderParsing.result();
            }
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(componentNodeValue);
            basicComponentDescriptions.put(
                    componentKey,
                    new BasicComponent(
                            ComponentDescription.ComponentDescriptionType.BasicComponent,
                            componentKey,
                            defaultValueParsing.result(),
                            oaTags,
                            importHeader,
                            exportHeaderName,
                            rootBuilder().getLangRestrictions(componentPath, componentNodeValue),
                            required,
                            mandatory,
                            checkerDescriptionParsing.result(),
                            null));

        }
        return i18n;
    }
}