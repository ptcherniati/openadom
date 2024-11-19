package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

public record FloatChecker(
        CheckerDescriptionType type,
        Multiplicity multiplicity,
        boolean required,
        Float min,
        Float max
) implements CheckerDescription {
    @Override
    public String comment() {
        return "Float";
    }

    @Override
    public String buildImportDataExempleForheader() {
        return "a float";
    }
}
