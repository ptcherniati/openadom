package fr.inra.oresing.domain.application.configuration;

public sealed interface Depends permits DependsParent, DependsRecursive, DependsReferences {
    String component();

    DependsType type();

    String references();

    enum DependsType {
        DependsParent,
        DependsRecursive,
        DependsReferences
    }
}
