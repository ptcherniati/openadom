package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.Optional;
import java.util.function.Predicate;

public record StringChecker(CheckerDescriptionType type,
                            Multiplicity multiplicity,
                            boolean required,
                            String pattern) implements CheckerDescription {
    @Override
    public String comment() {
        return Optional.ofNullable(pattern())
                .filter(Predicate.not(".*"::equals))
                .map("%s String"::formatted)
                .orElse("String");
    }
    @Override
    public String buildImportDataExempleForheader() {
        return Optional.ofNullable(pattern())
                .filter(Predicate.not(".*"::equals))
                .map("a string with pattern %s"::formatted)
                .orElse("a string");
    }
}
