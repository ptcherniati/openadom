package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.Set;

public record ComputationChecker(CheckerDescriptionType type,
                                 Multiplicity multiplicity,
                                 boolean required,
                                 String expression,
                                 Set<String> references,
                                 Set<String> exceptionMessages) implements CheckerDescription, TransformationConfiguration {
    @Override
    public boolean isCodify() {
        return false;
    }

    @Override
    public Set<String> datatypes() {
        return references();
    }
}
