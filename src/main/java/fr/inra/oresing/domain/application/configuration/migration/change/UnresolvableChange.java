package fr.inra.oresing.domain.application.configuration.migration.change;

public record UnresolvableChange(String propertyPath, String changeType, String leftValue, String rightValue) implements ConfigurationChange {
}