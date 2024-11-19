package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ComputedComponent;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;

public record ComputedComponentBuilder(RootBuilder rootBuilder) {

    I18n build(final String componentPath,
               final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder,
               final String dataKey,
               I18n i18n,
               final JsonNode jsonNode) {
        final JsonNode computedComponents = jsonNode.findPath(ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS);
        final Iterator<Map.Entry<String, JsonNode>> fields = computedComponents.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> componentEntry = fields.next();
            final JsonNode componentNodeValue = componentEntry.getValue();
            final boolean required = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(componentNodeValue);
            final String componentKey = componentEntry.getKey();
            final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(dataKey, i18n, componentEntry, ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS);
            String exportHeaderName = null;
            if (exportHeaderParsing != null) {
                i18n = exportHeaderParsing.i18n();
                exportHeaderName = exportHeaderParsing.result();
            }
            final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                    componentNodeValue,
                    NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                            componentKey,
                            ConfigurationSchemaNode.OA_TAGS
                    ),
                    rootBuilder);
            final Parsing<ComputationChecker> computationCheckerParsing;
            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder().getCheckerDescriptionBuilder().build(
                    i18n,
                    componentKey,
                    required,
                    NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                            componentKey,
                            ConfigurationSchemaNode.OA_CHECKER

                    ),
                    componentNodeValue.findPath(ConfigurationSchemaNode.OA_CHECKER),
                    dataKey
            );
            i18n = checkerDescriptionParsing.i18n();
            Multiplicity multiplicity = Optional.ofNullable(checkerDescriptionParsing.result())
                    .map(CheckerDescription::multiplicity)
                    .orElse(Multiplicity.ONE);

            final JsonNode computationNode = componentNodeValue.get(ConfigurationSchemaNode.OA_COMPUTATION);
            final JsonNode computationWithnaturalKeyNode = componentNodeValue.get(ConfigurationSchemaNode.OA_WITH_NATURAL_KEY_COMPONENTS);
            if (computationNode != null) {
                computationCheckerParsing = getComputationCheckerParsing(
                        i18n,
                        multiplicity,
                        componentPath,
                        required,
                        componentKey,
                        computationNode);
            } else {
                List<String> availableComponentKeys = rootBuilder().getListComponentKeys(dataKey);
                computationCheckerParsing = new Parsing<>(i18n,
                        getComputationCheckerWithNaturalKeyParsing(
                                multiplicity, componentPath, required, componentKey, computationWithnaturalKeyNode, availableComponentKeys)
                );
            }
            i18n = computationCheckerParsing.i18n();
            componentDescriptionBuilder.put(componentKey, new ComputedComponent(
                    ComponentDescription.ComponentDescriptionType.ComputedComponent,
                    componentKey,
                    oaTags,
                    exportHeaderName,
                    rootBuilder().getLangRestrictions(componentPath, componentNodeValue),
                    required,
                    mandatory,
                    checkerDescriptionParsing.result(),
                    computationCheckerParsing.result(),
                    null));
        }
        return i18n;
    }

    private Parsing<ComputationChecker> getComputationCheckerParsing(I18n i18n, Multiplicity multiplicity, String componentPath, boolean required, String componentKey, JsonNode computationNode) {
        final Parsing<ComputationChecker> computationCheckerParsing = rootBuilder.getComputationBuilder().build(
                i18n,
                required, multiplicity,
                NodeSchemaValidator.joinPath(
                        componentPath,
                        ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                        componentKey,
                        ConfigurationSchemaNode.OA_COMPUTATION),
                computationNode);
        i18n = computationCheckerParsing.i18n();
        if (computationCheckerParsing.result().getReferences() != null) {
            for (final String reference : computationCheckerParsing.result().getReferences()) {
                if (!rootBuilder.getListDataKeys().contains(reference)) {
                    rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                    "referenceName", reference,
                                    "allDataNames", rootBuilder.getListDataKeys()),
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                                    componentKey,
                                    ConfigurationSchemaNode.OA_DEFAULT_VALUE,
                                    ConfigurationSchemaNode.OA_REFERENCES
                            ));
                }
            }
        }
        return computationCheckerParsing;
    }


    private ComputationChecker getComputationCheckerWithNaturalKeyParsing(Multiplicity multiplicity, String componentPath, boolean required, String componentKey, JsonNode computationWithnaturalKeyNode, List<String> availableComponentKeys) {
        final ComputationChecker computationChecker = rootBuilder.getComputationBuilder().buildFromNaturalKey(required, multiplicity,
                NodeSchemaValidator.joinPath(
                        componentPath,
                        ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                        componentKey,
                        ConfigurationSchemaNode.OA_WITH_NATURAL_KEY_COMPONENTS),
                computationWithnaturalKeyNode,
                availableComponentKeys);
        if (computationChecker.getReferences() != null) {
            for (final String reference : computationChecker.getReferences()) {
                if (!rootBuilder.getListDataKeys().contains(reference)) {
                    rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                    "referenceName", reference,
                                    "allDataNames", rootBuilder.getListDataKeys()),
                            NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS,
                                    componentKey,
                                    ConfigurationSchemaNode.OA_DEFAULT_VALUE,
                                    ConfigurationSchemaNode.OA_REFERENCES
                            ));
                }
            }
        }
        return computationChecker;
    }
}
