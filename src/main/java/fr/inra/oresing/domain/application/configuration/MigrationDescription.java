package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public record MigrationDescription(Map<String, ComponentDescription> components,
                                   String dataGroup) {
} //TODO
