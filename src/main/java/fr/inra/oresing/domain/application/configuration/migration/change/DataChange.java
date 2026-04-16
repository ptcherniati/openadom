package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface DataChange extends ConfigurationChange permits
        ComponentChange, DataAdded, DataRemoved {
}