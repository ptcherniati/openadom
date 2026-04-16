package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface I18nChange extends ConfigurationChange permits
        I18nDisplayPattenChanged, I18nImportHeaderChange, I18nSimpleChange {
}