package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

public record ReferenceChecker(
        CheckerDescriptionType type,
        String componentKey,
        Multiplicity multiplicity,
        boolean required,
        String refType,
        boolean isRecursive,
        boolean isParent
) implements CheckerDescription {
    @Override
    public String comment() {
        return "%s Reference".formatted(refType());
    }

    @Override
    public String buildImportDataExempleForheader() {
        return "A value of %s".formatted(refType());
    }
}
