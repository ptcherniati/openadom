package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

public record IntegerChecker(CheckerDescriptionType type,
                             Multiplicity multiplicity,
                             boolean required,
                             Integer min,
                             Integer max) implements CheckerDescription {
    @Override
    public String comment() {
        return "Integer";
    }

    @Override
    public String buildImportDataExempleForheader() {
        return "an integer";
    }
}
