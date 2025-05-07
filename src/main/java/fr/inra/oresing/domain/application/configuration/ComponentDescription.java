package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;

public sealed interface ComponentDescription permits BasicComponent, ComputedComponent, ConstantComponent, DynamicComponent, FilteredDescriptionComponent, PatternComponent, PatternComponentAdjacents, PatternComponentQualifiers, ReferenceScopeComponent {
    ComponentDescriptionType type();
    default String getReferenceCheckerType(){
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

    default String importHeader() {
        return componentKey();
    }

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

    private Set<LineChecker> toLineChecker(DataRepository referenceValueRepository, PublishContext.PublishContextBuilder publishContextBuilder, Application application) {
        final DataColumn target = new DataColumn(componentKey());
        final LineChecker.LineTransformer lineTransformer = transformation() == null ?
                LineChecker.LineTransformer.NULL_LINE_TRANSFORMER :
                LineChecker.LineTransformer.newTransformer(
                        referenceValueRepository,
                        transformation(),
                        target,
                        publishContextBuilder,
                        Optional.ofNullable(checker())
                                .map(CheckerDescription::multiplicity)
                                .orElse(transformation().multiplicity())
                );
        final FieldType fieldType = checker().buildFieldtype(
                referenceValueRepository,
                publishContextBuilder,
                target,
                lineTransformer
        );
        return switch (checker().multiplicity()) {
            case ONE -> Set.of(new LineChecker.OneChecker<>(
                    fieldType,
                    target,
                    lineTransformer,
                    checker()
            ));
            case MANY -> Set.of(new LineChecker.ManyChecker<>(
                    new ListType<>(fieldType),
                    target,
                    lineTransformer,
                    checker()
            ));
        };
    }

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

    default Boolean isHiddenOrHasLangRestriction(String locale) {
        return hasLangRestriction(locale) ||
                isHidden();
    }

    default Boolean hasLangRestriction(String locale){
        if (CollectionUtils.isEmpty(langRestrictions())){
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