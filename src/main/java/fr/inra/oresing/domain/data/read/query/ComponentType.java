package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;

import java.util.Optional;

public sealed interface ComponentType permits
        ComponentTextType,
        ComponentReferenceType,
        ComponentNumericType,
        ComponentBooleanType,
        ComponentDateType {
}

