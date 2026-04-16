package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public class MigrationDescriptionBuilder {

    private Map<String, ComponentDescription> components = Map.of();
    private String dataGroup;

    public MigrationDescriptionBuilder components(Map<String, ComponentDescription> components) {
        this.components = components;
        return this;
    }

    public MigrationDescriptionBuilder dataGroup(String dataGroup) {
        this.dataGroup = dataGroup;
        return this;
    }

    public MigrationDescription build() {
        return new MigrationDescription(components, dataGroup);
    }
}