package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface ComponentChange extends DataChange permits
        ComponentAdded, ComponenRemoved, ComponentChanged{
}