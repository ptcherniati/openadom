package fr.inra.oresing.domain.application.configuration.migration.change;

public sealed interface CheckerChange extends ComponentChanged permits
        CheckerAdded, CheckerRemoved, CheckerModified {
}