package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

public record BooleanChecker(CheckerDescriptionType type,
                             Multiplicity multiplicity,
                             boolean required,
                             boolean isTrue) implements CheckerDescription {
    @Override
    public String comment() {
        return "Boolean";
    }
    @Override
    public String buildImportDataExempleForheader() {
        return "a boolean";
    }
}
