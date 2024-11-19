package fr.inra.oresing.domain.application.configuration;

public record DependsRecursive(DependsType type, String references, String component) implements Depends {
}
