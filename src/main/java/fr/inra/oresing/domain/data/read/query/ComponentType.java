package fr.inra.oresing.domain.data.read.query;

public sealed interface ComponentType permits
        ComponentTextType,
        ComponentReferenceType,
        ComponentNumericType,
        ComponentBooleanType,
        ComponentDateType {
}

