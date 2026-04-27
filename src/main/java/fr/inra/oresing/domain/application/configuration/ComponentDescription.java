package fr.inra.oresing.domain.application.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public sealed interface ComponentDescription permits BasicComponent, ComputedComponent, ConstantComponent, DynamicComponent, FilteredDescriptionComponent, PatternComponent, PatternComponentAdjacents, PatternComponentQualifiers, ReferenceScopeComponent {
    ComponentDescriptionType type();

    default String getReferenceCheckerType() {
        return findReferenceCheckerType().orElse(CheckerDescription.CheckerDescriptionType.StringChecker.name());
    }

    default Optional<String> findReferenceCheckerType() {
        return Optional.ofNullable(checker())
                .filter(checker -> CheckerDescription.CheckerDescriptionType.ReferenceChecker == checker.type())
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::refType);
    }

    String componentKey();

    default Set<Tag> tags() {
        return Set.of();
    }

    @JsonProperty("importHeader")
    default String importHeader() {
        return componentKey();
    }

    @JsonProperty("exportHeaderName")
    String exportHeaderName();

    default ComponentPresenceConstraint mandatory() {
        return ComponentPresenceConstraint.OPTIONAL;
    }

    CheckerDescription checker();

    default ComputationChecker defaultValue() {
        return null;
    }

    String submissionAuthorizationScope();

    List<Locale> langRestrictions();

    ComponentDescription withSubmission(String submission);

    default TransformationConfiguration transformation() {
        return null;
    }

    default ChartDescription getChartDescription() {
        return null;
    }

    default Boolean isHidden() {
        return Optional.ofNullable(tags())
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent();
    }

    /**
     * Vrai ssi la colonne porte le tag {@code __FILTER__} dans
     * {@code OA_tags} ; signal opt-in que le frontend traduit en
     * activation d'un filtre de recherche interactif sur cette colonne
     * ( cf. {@link Tag.FilterTag} ).
     */
    default boolean isFilterable() {
        return tags().stream().anyMatch(Tag.FilterTag.class::isInstance);
    }

    default Boolean isHiddenOrHasLangRestriction(String locale) {
        return hasLangRestriction(locale) ||
                isHidden();
    }

    default Boolean hasLangRestriction(String locale) {
        if (CollectionUtils.isEmpty(langRestrictions())) {
            return false;
        }
        return !langRestrictions().contains(Locale.of(locale));
    }


    default boolean isParent(String dataname) {
        return Optional.of(this)
                .map(ComponentDescription::checker)
                .filter(ReferenceChecker.class::isInstance)
                .map(ReferenceChecker.class::cast)
                .filter(checker -> !checker.refType().equals(dataname))
                .map(ReferenceChecker::isParent)
                .orElse(false);
    }

    default boolean isReference() {
        return Optional.ofNullable(checker())
                .filter(ReferenceChecker.class::isInstance)
                .isPresent();
    }

    default String buildImportHeaderForComponent() {
        return null;
    }

    default String buildImportDataExempleForComponent() {
        return null;
    }

    default boolean hasOrderTag() {
        return componentOrder() < 9999;

    }

    default Integer componentOrder() {
        return Optional.ofNullable(tags())
                .flatMap(tags -> tags.stream().filter(Tag.OrderTag.class::isInstance).findFirst())
                .map(Tag.OrderTag.class::cast)
                .map(Tag.OrderTag::tagOrder)
                .orElse(9999);
    }


    enum ComponentDescriptionType {
        AuthorizationScopeComponent,
        TagsDescription,
        ComputedComponent,
        DynamicComponent,
        BasicComponent,
        ConstantComponent,
        PatternComponent,
        PatternComponentQualifiers,
        PatternComponentAdjacents
    }

}