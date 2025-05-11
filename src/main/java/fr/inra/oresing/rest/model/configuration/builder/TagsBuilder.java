package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.Validation;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record TagsBuilder(RootBuilder rootBuilder) {

    public static Set<Tag> validateDomainTagNames(final JsonNode parentNode, final String path, final RootBuilder rootBuilder) {
        final Set<String> domainTagNames = rootBuilder
                .getDomainTags()
                .stream()
                .filter(Tag.DomainTag.class::isInstance)
                .map(Tag.DomainTag.class::cast)
                .map(Tag.DomainTag::tagName)
                .collect(Collectors.toSet());
        final Set<Tag> oaTags = Tag
                .buildTags(
                        rootBuilder
                                .getMapper()
                                .convertValue(
                                        Optional.ofNullable(parentNode.get(ConfigurationSchemaNode.OA_TAGS)).orElse(JsonNodeFactory.instance.missingNode()),
                                        HashSet.class),
                        new Validation(rootBuilder.getBuildErrorWithValidationParams(),
                                path,
                                Map.of("domainTags", domainTagNames)
                        )
                );
        final Set<String> listDomainTagsInComponent = oaTags
                .stream()
                .filter(Tag.DomainTag.class::isInstance)
                .filter(tag -> !rootBuilder.getDomainTags().contains(tag))
                .map(Tag.DomainTag.class::cast)
                .map(Tag.DomainTag::tagName)
                .collect(Collectors.toSet());
        if (!listDomainTagsInComponent.isEmpty()) {
            rootBuilder.buildError(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS, Map.of(
                            "notExpectedDomainTags", listDomainTagsInComponent,
                            "expectedDomainTags", domainTagNames),
                    path);

        }
        return oaTags;
    }

    private Parsing<Set<Tag>> buildTags(final String path, final JsonNode tagsNode, I18n i18n) {
        I18n i18n1 = i18n;
        final Map<String, Map<String, String>> tagsMap = (Map<String, Map<String, String>>) rootBuilder.getMapper().convertValue(tagsNode, Map.class);
        if (tagsMap == null) {
            return new Parsing<>(i18n1, Tag.buildTags(Set.of(),
                    new Validation(rootBuilder.getBuildErrorWithValidationParams(), path, null)));
        }
        for (final Map.Entry<String, Map<String, String>> entry : tagsMap.entrySet()) {
            try {
                i18n1 = i18n1.add(NodeSchemaValidator.joinI18nPath(Internationalizations.TAGS, entry.getKey()), entry.getValue());
            } catch (final IllegalArgumentException illegalArgumentException) {
                rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                        Map.of(),
                        "OA_tags > %1$s".formatted(entry.getKey()));
            }

        }
        return new Parsing<>(
                i18n1,
                Tag
                        .buildTags(
                                tagsMap.keySet(),
                                new Validation(rootBuilder.getBuildErrorWithValidationParams(), path, Map.of("onlyDomainTags", true, "domainTags", rootBuilder.getDomainTags()))
                        )
        );
    }

    Parsing<Set<Tag>> buildDomainTagsOfApplication(final String path, final JsonNode tagsNode, final I18n i18n) {
        if(tagsNode== null || tagsNode.isMissingNode() || tagsNode.isNull() || tagsNode.isEmpty()){
            return new Parsing<>(i18n,Set.of());
        }
        final Parsing<Set<Tag>> parseTag = buildTags(path, tagsNode, i18n);
        if (parseTag.result().stream().anyMatch(tag -> !(tag instanceof Tag.DomainTag))) {
            final Set<String> reservedTagNames = parseTag.result()
                    .stream()
                    .filter(tag -> !(tag instanceof Tag.DomainTag))
                    .map(Tag::toString)
                    .collect(Collectors.toSet());
            rootBuilder.buildError(ConfigurationException.ILLEGAL_DOMAIN_TAG_PATTERN,
                    Map.of(
                            "expectedPattern", Tag.DomainTag.DOMAIN_PATTERN,
                            "reservedTagNames", reservedTagNames
                    ),
                    path);
        }
        return parseTag;
    }

    /*Parsing<Set<Tag>> buildSectionTags(String path, JsonNode tagsNode, I18n i18n) {
        Parsing<Set<Tag>> parseTag = buildTags(path, tagsNode, i18n);
        //TODO test all domain tags are in domainTags
        return parseTag;
    }*/
}