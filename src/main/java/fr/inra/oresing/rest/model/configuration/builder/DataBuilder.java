package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record DataBuilder(RootBuilder rootBuilder) {

    public static final Pattern DISPLAY_MATCHING_GROUP = Pattern.compile("\\{([^}]+)\\}");

    Parsing<StandardDataDescription> build(final String path, final String dataKey, final JsonNode jsonNode, I18n i18n) {
        final Set<Tag> tags = buildAndTestDomainTags(path, jsonNode);

        final Integer headerLine = Optional.ofNullable(jsonNode.get(ConfigurationSchemaNode.OA_HEADER_LINE))
                .map(JsonNode::asInt)
                .orElse(1);
        final Integer firstRowLine = Optional.ofNullable(jsonNode.get(ConfigurationSchemaNode.OA_FIRST_ROW_LINE))
                .map(JsonNode::asInt)
                .orElse(2);
        final Boolean allowUnexpectedColumns = Optional.ofNullable(jsonNode.get("OA_allowUnexpectedColumns"))
                .map(JsonNode::asBoolean)
                .orElse(false);
        final Map localizationNames = rootBuilder.getMapper().convertValue(jsonNode.findPath(ConfigurationSchemaNode.OA_I_18_N), Map.class);
        try {
            i18n = i18n.add(
                    NodeSchemaValidator.joinI18nPath(
                            "data",
                            dataKey,
                            InternationalizationData.I18N
                    ),
                    rootBuilder.getMapper().convertValue(localizationNames, Map.class));

        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey,
                            ConfigurationSchemaNode.OA_I_18_N
                    )
            );
        }
        final Map localizationPatternNames = rootBuilder.getMapper().convertValue(jsonNode.findPath(ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN), Map.class);

        try {
            i18n = i18n.add(
                    NodeSchemaValidator.joinI18nPath(
                            "data",
                            dataKey,
                            InternationalizationData.I18N_DISPLAY_PATTERN
                    ),
                    rootBuilder.getMapper().convertValue(localizationPatternNames, Map.class));

        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey,
                            ConfigurationSchemaNode.OA_I_18_N
                    )
            );
        }
        final List<String> listComponentKeys = rootBuilder.getListComponentKeys(dataKey);

        final Map<String, Map> localizationDisplay = rootBuilder.getMapper().convertValue(
                jsonNode.findPath(ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN), Map.class);
        testLocalizationDisplay(localizationDisplay, listComponentKeys, path);
        try {
            i18n = i18n.add(
                    NodeSchemaValidator.joinI18nPath(
                            Internationalizations.DATA,
                            dataKey,
                            InternationalizationData.I18N_DISPLAY_PATTERN
                    ), localizationDisplay);
        } catch (final IllegalArgumentException illegalArgumentException) {
            rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                    Map.of(),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey,
                            ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN,
                            ConfigurationSchemaNode.OA_PATTERN
                    )
            );
        }
        final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder = new ImmutableMap.Builder<>();
        i18n = rootBuilder.getBasicComponentBuilder().build(path, componentDescriptionBuilder, dataKey, i18n, jsonNode);
        i18n = rootBuilder.getComputedComponentBuilder().build(path, componentDescriptionBuilder, dataKey, i18n, jsonNode);
        i18n = rootBuilder.getDynamicComponentsBuilder().build(path, componentDescriptionBuilder, dataKey, i18n, jsonNode);
        i18n = rootBuilder.getPatternComponentsBuilder().build(path, componentDescriptionBuilder, dataKey, i18n, jsonNode);
        i18n = rootBuilder.getConstantComponentsBuilder().build(path, componentDescriptionBuilder, dataKey, i18n, jsonNode, headerLine, firstRowLine);
        ImmutableMap<String, ComponentDescription> componentDescriptions = componentDescriptionBuilder.build();
        Map<String, List<Map.Entry<String, ComponentDescription>>> duplicatedImportHeader = componentDescriptions.entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().importHeader()
                )).entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> !(entry.getValue().stream().allMatch(value -> (value.getValue() instanceof PatternComponentQualifiers) || (value.getValue() instanceof PatternComponentAdjacents))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (MapUtils.isNotEmpty(duplicatedImportHeader)) {
            duplicatedImportHeader.forEach((key, value) -> rootBuilder.buildError(ConfigurationException.DUPLICATED_COMPONENT_HEADER,
                    Map.of(
                            "data", dataKey,
                            "duplicatedHeader", key,
                            "duplicatedImportHeader", value.stream().map(Map.Entry::getKey).toList()
                    ),
                    NodeSchemaValidator.joinPath(
                            ConfigurationSchemaNode.OA_DATA,
                            dataKey
                    )
            ));
        }
        final ImmutableMap.Builder<String, ValidationDescription> validationBuilder = new ImmutableMap.Builder<>();
        i18n = rootBuilder.getValidationsBuilder().build(path, validationBuilder, dataKey, i18n, jsonNode, componentDescriptions);
        ImmutableMap<String, ValidationDescription> validations = validationBuilder.build();
        Map<CheckerDescription.CheckerDescriptionType, List<String>> componentValidationsByType = getComponentValidationsByType(validations);
        HashMap<String, ComponentDescription> localComponentDescription = new HashMap<>(componentDescriptions);
        final Parsing<Submission> submissionParsing = rootBuilder.getSubmissionBuilder().buildSubmission(i18n, dataKey, jsonNode, localComponentDescription);
        if (
                tags.stream().noneMatch(Tag.DataTag.class::isInstance) &&
                Optional.of(submissionParsing).map(Parsing::result).map(Submission::strategy).filter(SubmissionType.OA_VERSIONING::equals).isPresent()
        ) {
            rootBuilder.buildError(ConfigurationException.UNEXPECTED_SUBMISSION,
                    Map.of()
                    , path);

        }
        componentDescriptions = ImmutableMap.copyOf(localComponentDescription);
        i18n = submissionParsing.i18n();
        Authorization authorization = rootBuilder().getAuthorizationBuilder().build(path, dataKey, jsonNode, componentDescriptions, componentValidationsByType);
        final char separator = Optional.ofNullable(jsonNode.get(ConfigurationSchemaNode.OA_SEPARATOR)).map(JsonNode::asText).map(t -> t.charAt(0)).orElse(';');
        final LinkedHashSet<String> expectedComponentsLabel = new LinkedHashSet<>(componentDescriptions.keySet());
        final LinkedHashSet<String> naturalKeys = Optional.ofNullable(jsonNode.get(ConfigurationSchemaNode.OA_NATURAL_KEY))
                .map(node -> rootBuilder.getMapper().convertValue(node, LinkedHashSet.class))
                .orElse(expectedComponentsLabel);
        final Set<String> invalidNaturalKeyElements = naturalKeys.stream()
                .filter(naturalKey -> !expectedComponentsLabel.contains(naturalKey))
                .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(invalidNaturalKeyElements)) {
            rootBuilder.buildError(ConfigurationException.INVALID_NATURAL_KEY,
                    Map.of("invalidNaturalKeyElements", invalidNaturalKeyElements,
                            "expectedComponentLabel", expectedComponentsLabel)
                    , path);
        }
        return new Parsing<>(
                i18n,
                new StandardDataDescription(
                        separator,
                        headerLine,
                        firstRowLine,
                        allowUnexpectedColumns,
                        tags,
                        naturalKeys,
                        componentDescriptions,
                        submissionParsing.result(),
                        authorization,
                        validations,
                        null
                ));
    }

    private boolean testLocalizationDisplay(Map<String, Map> localizationDisplay, List<String> listComponentKeys, String path) {
        if (MapUtils.isEmpty(localizationDisplay)) {
            return true;
        }
        for (String group : List.of(ConfigurationSchemaNode.OA_TITLE, ConfigurationSchemaNode.OA_DESCRIPTION)) {
            if (localizationDisplay.containsKey(group)) {
                for (Object language : localizationDisplay.get(group).keySet()) {
                    boolean test = testLocalizationDisplay(
                            localizationDisplay.get(group).get(language).toString(),
                            listComponentKeys,
                            path,
                            group,
                            language.toString());
                    if (!test) {
                        return test;
                    }
                }
            }
        }
        return true;
    }

    private boolean testLocalizationDisplay(String matchingGroup, List<String> listComponentKeys, String path, String group, String language) {
        Pattern pattern = DISPLAY_MATCHING_GROUP;
        List<String> list = pattern.matcher(matchingGroup)
                .results()
                .map(m -> m.group(1))
                .filter(Predicate.not(listComponentKeys::contains))
                .toList();
        list.stream().forEach(
                badGroup -> rootBuilder.buildError(ConfigurationException.MISSING_COMPONENT_FOR_DISPLAY_PATTERN,
                        Map.of(
                                "badGroup", badGroup,
                                "expectedComponent", listComponentKeys
                        ),
                        NodeSchemaValidator.joinPath(
                                path,
                                group,
                                language
                        )
                )
        );
        return list.isEmpty();
    }

    private Map<CheckerDescription.CheckerDescriptionType, List<String>> getComponentValidationsByType(ImmutableMap<String, ValidationDescription> validations) {
        Map<CheckerDescription.CheckerDescriptionType, List<String>> componentValidationByType = new HashMap<>();
        for (ValidationDescription validation : validations.values()) {
            if (MapUtils.isNotEmpty(validation.checkers())) {
                for (Map.Entry<String, CheckerDescription> componentCheckerEntry : validation.checkers().entrySet()) {
                    String componentName = componentCheckerEntry.getKey();
                    CheckerDescription.CheckerDescriptionType checkerType = componentCheckerEntry.getValue().type();
                    componentValidationByType
                            .computeIfAbsent(checkerType, k -> new ArrayList<>())
                            .add(componentName);
                }
            }
        }
        return componentValidationByType;
    }


    private Set<Tag> buildAndTestDomainTags(final String path, final JsonNode jsonNode) {
        if (jsonNode.get(ConfigurationSchemaNode.OA_TAGS) == null) {
            return Collections.emptySet();
        }
        final Set<String> domainTagNames = rootBuilder.getDomainTags().stream()
                .filter(Tag.DomainTag.class::isInstance)
                .map(Tag.DomainTag.class::cast)
                .map(Tag.DomainTag::tagName)
                .collect(Collectors.toSet());
        final Set<Tag> tags = Tag
                .buildTags(
                        rootBuilder.getMapper().convertValue(
                                jsonNode.findPath(ConfigurationSchemaNode.OA_TAGS),
                                Set.class
                        ),
                        new Validation(rootBuilder.getBuildErrorWithValidationParams(), path, Map.of("domainTags", domainTagNames))
                );
        final Set<String> notExpectedDomainTags = tags.stream()
                .filter(Tag.DomainTag.class::isInstance)
                .filter(tag -> !rootBuilder.getDomainTags().contains(tag))
                .map(Tag.DomainTag.class::cast)
                .map(Tag.DomainTag::tagName)
                .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(notExpectedDomainTags)) {
            rootBuilder.buildError(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS,
                    Map.of("notExpectedDomainTags", notExpectedDomainTags,
                            "expectedDomainTags", domainTagNames)
                    , path);
        }
        return tags;
    }
}
