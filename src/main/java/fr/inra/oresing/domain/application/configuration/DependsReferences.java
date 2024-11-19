package fr.inra.oresing.domain.application.configuration;

public record DependsReferences(DependsType type, String references, String component) implements Depends {
}
