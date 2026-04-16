package fr.inra.oresing.domain.application.configuration.migration.change;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;

public record DataRemoved(String dataName, StandardDataDescription dataDescription) implements DataChange {
    public static final String NAME = "DataRemoved";
    public static final String DESCRIPTION = "Removing a data type";
}