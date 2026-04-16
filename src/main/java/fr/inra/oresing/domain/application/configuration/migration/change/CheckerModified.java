package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface CheckerModified extends CheckerChange permits CheckerTypeChanged, CheckerDefinitionChanged {
}