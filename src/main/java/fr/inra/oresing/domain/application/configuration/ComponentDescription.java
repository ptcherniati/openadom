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
     * Vrai ssi la colonne est filtrable au sens "tag {@code __FILTER_*__} posé ET
     * compatible avec le checker" : porteuse de {@link Tag.FilterTextTag} ou
     * {@link Tag.FilterListTag} , et non couverte par un filtre natif ( Date , Integer ,
     * Float , Boolean , Reference - voir {@link #hasNativeFilter()} ) , et non masquée
     * par {@link Tag.HiddenTag}.
     *
     * <p>Sert au frontend à décider s'il rend un champ filtre pour la colonne. La
     * distinction texte vs liste se fait via {@link #isFilterableAsText()}.
     */
    default boolean isFilterable() {
        return isFilterableAsText() || isFilterableAsList();
    }

    /**
     * Vrai ssi la colonne porte {@link Tag.FilterTextTag} et que le tag n'est pas
     * neutralisé par la règle d'exclusion ( voir {@link #hasFilterTagSuppressed()} ).
     */
    default boolean isFilterableAsText() {
        return !hasFilterTagSuppressed()
                && tags().stream().anyMatch(Tag.FilterTextTag.class::isInstance);
    }

    /**
     * Vrai ssi la colonne porte {@link Tag.FilterListTag} et que le tag n'est pas
     * neutralisé par la règle d'exclusion. Implicite au front quand
     * {@link #isFilterable()} est vrai et {@link #isFilterableAsText()} est faux ;
     * exposé séparément pour la lisibilité des tests.
     */
    default boolean isFilterableAsList() {
        return !hasFilterTagSuppressed()
                && tags().stream().anyMatch(Tag.FilterListTag.class::isInstance);
    }

    /**
     * Règle d'exclusion : un tag {@code __FILTER_*__} est ignoré si la colonne est
     * masquée ( {@link Tag.HiddenTag} ) ou si son checker dispose déjà d'un filtre
     * natif rendu automatiquement par l'UI ( Date , Integer , Float , Boolean ,
     * Reference ). Dans ces cas , poser le tag dans le YAML reste tolerant ( pas
     * d'erreur de chargement ) mais l'UI n'en tient pas compte.
     */
    default boolean hasFilterTagSuppressed() {
        return isHidden() || hasNativeFilter();
    }

    /**
     * Vrai ssi le checker de la colonne est un de ceux pour lesquels l'UI rend
     * d'emblée un filtre adapté ( intervalle , toggle , dropdown référence ).
     * Ces filtres natifs priment sur les tags {@code __FILTER_TEXT__} /
     * {@code __FILTER_LIST__} qui n'apporteraient qu'un doublon.
     */
    default boolean hasNativeFilter() {
        return Optional.ofNullable(checker())
                .map(CheckerDescription::type)
                .map(type -> switch (type) {
                    case BooleanChecker, DateChecker, FloatChecker, IntegerChecker,
                         ReferenceChecker -> true;
                    case ComputationChecker, GroovyExpressionChecker, StringChecker -> false;
                })
                .orElse(false);
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