package fr.inra.oresing.checker;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Indique si une valeur est unique ou si elle est multi-valuée (relation 1-1 ou 1-*)
 */
public enum Multiplicity {

    /**
     * Indique qu'une donnée est liée à un référentiel avec une multiplicité de un pour un
     */
    ONE,

    /**
     * Indique qu'une donnée est liée à un référentiel avec une multiplicité de plusieurs pour un
     */
    MANY;

    public static SiOreIllegalArgumentException getError(Multiplicity multiplicity) {
        return new SiOreIllegalArgumentException(
                "badMultiplicity",
                Map.of(
                        "multiplicity", multiplicity,
                        "knownMultiplicity", Arrays.stream(Multiplicity.values()).map(Multiplicity::toString).collect(Collectors.toSet())
                )
        );
    }
}