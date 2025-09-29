package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.Optional;

record Component(
        String fieldName,
        ComponentDescription fieldDescription,
        boolean isHidden,
        CheckerDescription.CheckerDescriptionType type,
        Multiplicity multiplicity,
        String refType,
        boolean isAuthorizationTimeScopeField,
        boolean isAuthorizationAuthorizationScopeField
) {
    static Component of(
            String fieldName,
            ComponentDescription fieldDescription,
            boolean isAuthorizationTimeScopeField,
            boolean isAuthorizationAuthorizationScopeField) {
        final Optional<CheckerDescription> checkerDescription = Optional.of(fieldDescription)
                .map(ComponentDescription::checker);
        final CheckerDescription.CheckerDescriptionType type = checkerDescription
                .map(CheckerDescription::type)
                .orElse(null);
        final Multiplicity multiplicity = checkerDescription
                .map(CheckerDescription::multiplicity)
                .orElse(Multiplicity.ONE);

        final String refType = checkerDescription
                .filter(ReferenceChecker.class::isInstance)
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::refType)
                .orElse(null);

        return new Component(
                fieldName,
                fieldDescription,
                fieldDescription.isHidden(),
                type,
                multiplicity,
                refType,
                isAuthorizationTimeScopeField,
                isAuthorizationAuthorizationScopeField
        );
    }
}