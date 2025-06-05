package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.ValidationDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.GroovyExpressionChecker;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public record ValidationsBuilder(RootBuilder rootBuilder) {

    I18n build(final String componentPath,
               final ImmutableMap.Builder<String, ValidationDescription> validationsBuilder,
               final String key,
               I18n i18n,
               final JsonNode jsonNode,
               final ImmutableMap<String, ComponentDescription> componentDescription) {
        final JsonNode validations = jsonNode.findPath(ConfigurationSchemaNode.OA_VALIDATIONS);
        final Iterator<Map.Entry<String, JsonNode>> fields = validations.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> validationEntry = fields.next();
            final JsonNode componentNodeValue = validationEntry.getValue();
            final Map localizationNames = rootBuilder.getMapper().convertValue(componentNodeValue.findPath(ConfigurationSchemaNode.OA_I_18_N), Map.class);
            final String componentKey = validationEntry.getKey();
            i18n = buildInternationalizations(key, i18n, componentKey, localizationNames);
            final Set<Tag> oaTags = buildTags(key, componentNodeValue, componentKey);
            final boolean required = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(componentNodeValue);
            final Set<String> columns = rootBuilder.getMapper()
                    .convertValue(Optional.of(componentNodeValue)
                                    .map(component -> component.get(ConfigurationSchemaNode.OA_COMPONENTS))
                                    .orElseGet(NullNode::getInstance),
                            Set.class);

            final List<String> listComponentKey = rootBuilder.getListComponentKeys(key);

            Map<String, CheckerDescription> checkers = new HashMap<>();
            if (columns == null) {
                i18n = buildGroovyForNullColumn(i18n, componentPath, key, componentKey, required, componentNodeValue, listComponentKey);
            } else if (columns.isEmpty()) {
                i18n = buildGroovyForEmptyColumn(i18n, componentPath, key, componentKey, required, componentNodeValue, listComponentKey);
            } else {
                i18n = buildCheckersForColumn(i18n, componentPath, key, columns, required, componentKey, componentNodeValue, listComponentKey, checkers);
            }
            validationsBuilder.put(
                    componentKey,
                    new ValidationDescription(checkers, oaTags, columns, required, mandatory));
        }
        return i18n;
    }

    private I18n buildCheckersForColumn(I18n i18n, String componentPath, String key, Set<String> columns, boolean required, String componentKey, JsonNode componentNodeValue, List<String> listComponentKey, Map<String, CheckerDescription> checkers) {
        AtomicReference<I18n> atomicReferenceI18n = new AtomicReference<>(i18n);
        columns.forEach(column -> {
            Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder
                    .getCheckerDescriptionBuilder()
                    .build(
                            atomicReferenceI18n.get(),
                            column,
                            required,
                            "%1$s > OA_validations > %2$s > OA_validations".formatted(
                                    componentPath, componentKey),
                            componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                            key
                    );

            atomicReferenceI18n.set(Objects.requireNonNull(checkerDescriptionParsing).i18n());
            if (!listComponentKey.contains(column)) {
                rootBuilder.buildError(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                                "unknownComponent", column,
                                "knownComponents", listComponentKey),
                        NodeSchemaValidator.joinPath(
                                ConfigurationSchemaNode.OA_DATA,
                                key,
                                ConfigurationSchemaNode.OA_VALIDATIONS,
                                componentKey,
                                ConfigurationSchemaNode.OA_COMPONENTS
                        ));
            }
            checkers.put(column, checkerDescriptionParsing.result());
        });
        return atomicReferenceI18n.get();
    }

    private I18n buildGroovyForEmptyColumn(I18n i18n, String componentPath, String key, String componentKey, boolean required, JsonNode componentNodeValue, List<String> listComponentKey) {
        Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder()
                .build(i18n,
                        componentKey,
                        required,
                        "%2$s > OA_validations > %2$s > OA_validations".formatted(componentPath, componentKey),
                        componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                        key);

        i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
        if (!(checkerDescriptionParsing.result() instanceof GroovyExpressionChecker)) {
            rootBuilder.buildError(ConfigurationException.MISSING_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                            "knownComponents", listComponentKey),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            key,
                            ConfigurationSchemaNode.OA_VALIDATIONS,
                            componentKey,
                            ConfigurationSchemaNode.OA_COMPONENTS
                    ));
        }
        return i18n;
    }

    private I18n buildGroovyForNullColumn(I18n i18n, String componentPath, String key, String componentKey, boolean required, JsonNode componentNodeValue, List<String> listComponentKey) {
        Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder.getCheckerDescriptionBuilder()
                .build(
                        i18n,
                        componentKey,
                        required,
                        "%2$s > OA_validations > %2$s > OA_validations".formatted(componentPath, componentKey),
                        componentNodeValue.get(ConfigurationSchemaNode.OA_CHECKER),
                        key);
        i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
        if (!(checkerDescriptionParsing.result() instanceof GroovyExpressionChecker)) {

            rootBuilder.buildError(ConfigurationException.MISSING_COLUMN_NAME_VALIDATION, Map.of(
                            "knownComponents", listComponentKey),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            key,
                            ConfigurationSchemaNode.OA_VALIDATIONS,
                            componentKey,
                            ConfigurationSchemaNode.OA_COMPONENTS
                    )
            );
        }
        return i18n;
    }


    private Set<Tag> buildTags(String key, JsonNode componentNodeValue, String componentKey) {
        return TagsBuilder.validateDomainTagNames(
                componentNodeValue,
                NodeSchemaValidator.joinPath(
                        ConfigurationSchemaNode.OA_DATA,
                        key,
                        ConfigurationSchemaNode.OA_VALIDATIONS,
                        componentKey,
                        ConfigurationSchemaNode.OA_TAGS
                ),
                rootBuilder);
    }


    private I18n buildInternationalizations(String key, I18n i18n, String componentKey, Map localizationNames) {
        try {
            i18n = i18n.add(
                    NodeSchemaValidator.joinI18nPath(
                            Internationalizations.DATA,
                            key,
                            InternationalizationData.VALIDATIONS,
                            componentKey
                    ),
                    rootBuilder.getMapper().convertValue(localizationNames, Map.class));
        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            key,
                            ConfigurationSchemaNode.OA_VALIDATIONS,
                            componentKey
                    )
            );
        }
        return i18n;
    }
}