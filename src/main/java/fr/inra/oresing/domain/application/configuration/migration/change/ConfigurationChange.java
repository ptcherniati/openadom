package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface ConfigurationChange permits DataChange, I18nChange, UnresolvableChange, IgnorableChange {
    public enum FactType{
        ADD,
        REMOVE,
        MODIFY
    }
}