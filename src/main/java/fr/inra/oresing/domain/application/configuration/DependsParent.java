package fr.inra.oresing.domain.application.configuration;

public record DependsParent(DependsType type, String references, String component) implements Depends {
}
