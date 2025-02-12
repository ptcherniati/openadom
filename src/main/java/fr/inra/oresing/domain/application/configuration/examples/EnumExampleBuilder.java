package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.application.configuration.type.EnumType;

import java.util.Optional;

class EnumExampleBuilder {
    private EnumExampleBuilder() {
    }

    protected static EnumType buildMultiplicityType(Multiplicity multiplicity) {
        multiplicity = multiplicity == null ? Multiplicity.ONE : multiplicity;
        return Optional.of(multiplicity)
                .map(Multiplicity::name)
                .map(name ->
                        new EnumType(
                                name,
                                EnumType.MULTIPLICITY_ENUM.values(),
                                false
                        )
                )
                .orElseThrow();
    }
}