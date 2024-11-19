package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.application.configuration.type.EnumType;

import java.util.Optional;

class EnumExampleBuilder {
    protected static EnumType buildMultiplicityType(Multiplicity multiplicity) {
        Multiplicity multiplicity1 = multiplicity == null ? Multiplicity.ONE : multiplicity;
        return Optional.ofNullable(multiplicity1)
                .map(Multiplicity::name)
                .map(name ->
                        new EnumType(
                                name,
                                EnumType.MULTIPLICITY_ENUM.values(),
                                false
                        )
                )
                .get();
    }
}