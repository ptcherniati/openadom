package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface ComponentChanged extends ComponentChange permits
        AuthorizationChanged, CheckerChange, HierarchieChanged, I18nDisplayPattenChanged, I18nImportHeaderChange, NaturalKeyChanged, SubmissionChanged {
}