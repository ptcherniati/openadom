package fr.inra.oresing.domain.application.configuration.section;

import com.google.common.base.Predicate;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.LabelDescription;
import fr.inra.oresing.domain.application.configuration.type.StaticMapType;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.model.configuration.builder.NodeSchemaValidator;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;

import java.util.*;
import java.util.stream.Collectors;

public class SectionBuilder {
    @Getter
    private final Set<Section> sections = new HashSet<>();

    @Getter
    private final Set<Section.AnyOfMandatorySection> anyOfMandatorySections = new HashSet<>();
    private boolean withLocalType;
    private boolean withAnySections;

    public static SectionBuilder getInstance() {
        return new SectionBuilder();
    }

    public static SectionBuilder ofAny() {
        return new SectionBuilder().withAnySections();
    }

    public static Set<Section.OptionalSection> buildOptionalSections(final LabelDescription... optionalSections) {
        return Arrays.stream(optionalSections)
                .map(Section.OptionalSection::buildInstance)
                .collect(Collectors.toSet());
    }

    public static Set<Section.MandatorySection> buildMandatorySections(final LabelDescription... mandatorySections) {
        return Arrays.stream(mandatorySections)
                .map(Section.MandatorySection::buildInstance)
                .collect(Collectors.toSet());
    }

    public static Collection<Section.AnyOfMandatorySection> buildAnyOfMandatorySections(final LabelDescription[] anyOfMandatorySections) {
        return Arrays.stream(anyOfMandatorySections)
                .map(Section.AnyOfMandatorySection::buildInstance)
                .collect(Collectors.toSet());
    }

    private SectionBuilder withAnySections() {
        withAnySections = true;
        return this;
    }

    public SectionBuilder withOptionalSections(final LabelDescription... optionalSections) {
        sections.addAll(buildOptionalSections(optionalSections));
        return this;
    }

    public SectionBuilder withMandatorySections(final LabelDescription... mandatorySections) {
        sections.addAll(buildMandatorySections(mandatorySections));
        return this;
    }

    public SectionBuilder withAnyOfMandatorySections(final LabelDescription... anyOfMandatorySections) {
        this.anyOfMandatorySections.addAll(buildAnyOfMandatorySections(anyOfMandatorySections));
        return this;
    }

    Set<Section.OptionalSection> getOptionalSections() {
        return getSections().stream()
                .filter(Section.OptionalSection.class::isInstance)
                .map(Section.OptionalSection.class::cast)
                .collect(Collectors.toSet());
    }

    Set<Section.MandatorySection> getMandatorySections() {
        return getSections().stream()
                .filter(Section.MandatorySection.class::isInstance)
                .map(Section.MandatorySection.class::cast)
                .collect(Collectors.toSet());
    }

    public Set<Section> getAllSections() {
        return SetUtils.union(sections, anyOfMandatorySections);
    }

    public SectionBuilder test(final Set<String> givenSections) {
        if (withAnySections) {
            return this;
        }
        final Set<String> badLocalLabels = new HashSet<>();
        if (withLocalType) {
            for (final String label : givenSections) {
                try {
                    Locale.of(label);
                } catch (final Exception e) {
                    badLocalLabels.add(label);
                }
            }
            return this;
        }
        if (CollectionUtils.isNotEmpty(badLocalLabels)) {
            throw new SiOreConfigurationFormatException(ConfigurationException.BAD_LOCALE_LABELS, Map.of("badLocalLabels", badLocalLabels));
        }
        final Set<String> unexpectedSections = givenSections.stream()
                .filter(this::isUnexpected)
                .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(unexpectedSections)) {
            throw new SiOreConfigurationFormatException(ConfigurationException.UNEXPECTED_SECTIONS,
                    Map.of(
                            "unexpectedSections", unexpectedSections,
                            "expectedSections", getAllSections().stream().map(Section::label).collect(Collectors.toSet())
                    )
            );
        }
        final Predicate<String> isMissingMandatorySection = s -> !givenSections.contains(s);
        final Predicate<String> isInGiven = s -> givenSections.contains(s);
        final Set<String> missingMandatorySections = getMandatorySections()
                .stream()
                .map(Section::label)
                .filter(isMissingMandatorySection)
                .collect(Collectors.toSet());
        if (CollectionUtils.isNotEmpty(missingMandatorySections)) {
            throw new SiOreConfigurationFormatException(ConfigurationException.MISSING_MANDATORIES_SECTIONS, Map.of("missingMandatoriesSections", missingMandatorySections));
        }
        if (CollectionUtils.isEmpty(getAnyOfMandatorySections())) {
            return this;
        }
        final boolean hasNoAnyOfMandatorySections = getAnyOfMandatorySections()
                .stream()
                .map(Section::label)
                .noneMatch(isInGiven);
        if (hasNoAnyOfMandatorySections) {
            throw new SiOreConfigurationFormatException(ConfigurationException.MISSING_ANY_MANDATORIES_SECTIONS, Map.of("anyMandatorySections", anyOfMandatorySections.stream().map(Section.AnyOfMandatorySection::label).collect(Collectors.toCollection(TreeSet::new))));
        }
        return this;
    }

    private boolean isUnexpected(final String section) {
            return getAllSections().stream()
                .map(Section::label)
                .noneMatch(label -> label.equals(section));
    }

    public SectionBuilder withLocalType() {
        withLocalType = true;
        return this;
    }

    public Optional<ConfigurationSchemaNodeType> findSchema(final String childLabel) {
        return switch (childLabel) {
            case NodeSchemaValidator.REFERENCE_SCOPES_FOR_FILE->
                    Optional.of(StaticMapType.REFERENCE_SCOPES_FOR_FILE().type());
            case ConfigurationSchemaNode.OA_ADDITIONAL_FILES ->
                    Optional.of(StaticMapType.ADDITIONAL_FILES().type());
            case ConfigurationSchemaNode.OA_DATA ->
                    Optional.of(StaticMapType.DATA().type());
            case ConfigurationSchemaNode.OA_VALIDATIONS ->
                    Optional.of(StaticMapType.VALIDATIONS().type());
            case ConfigurationSchemaNode.OA_REFERENCE_SCOPES ->
                    Optional.of(StaticMapType.REFERENCE_SCOPES().type());
            case ConfigurationSchemaNode.OA_FORM_FIELDS ->
                    Optional.of(StaticMapType.FORMATS().type());
            case ConfigurationSchemaNode.OA_BASIC_COMPONENTS ->
                    Optional.of(StaticMapType.BASIC_COMPONENTS().type());
            case ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS ->
                    Optional.of(StaticMapType.DYNAMIC_COMPONENTS().type());
            case ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS ->
                    Optional.of(StaticMapType.CONSTANT_COMPONENTS().type());
            case ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS ->
                    Optional.of(StaticMapType.COMPUTED_COMPONENTS().type());
            case ConfigurationSchemaNode.OA_PATTERN_COMPONENTS ->
                    Optional.of(StaticMapType.PATTERN_COMPONENTS().type());
            case ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS ->
                    Optional.of(StaticMapType.PATTERN_COMPONENTS_QUALIFIERS().type());
            case ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS ->
                    Optional.of(StaticMapType.PATTERN_COMPONENTS_ADJACENT().type());
            case null, default -> getAllSections().stream()
                    .filter(section -> section.matches(childLabel))
                    .map(Section::type)
                    .findFirst();
        };
    }
}
