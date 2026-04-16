package fr.inra.oresing.domain.application.configuration.migration.change;

import fr.inra.oresing.domain.application.configuration.StandardDataDescription;

public record DataAdded(String dataName, StandardDataDescription dataDescription) implements DataChange {
    public static final String NAME = "DataAdded";
    public static final String DESCRIPTION = "Adding a data type";
    public static final int PRIORITY = 10 ;
}