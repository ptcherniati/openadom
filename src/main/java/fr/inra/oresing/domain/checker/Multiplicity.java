package fr.inra.oresing.domain.checker;

import java.util.Arrays;
import java.util.Set;
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
    public static final Set<String> VALUES = Arrays.stream(values()).map(Multiplicity::name).collect(Collectors.toSet());

    /*public static SiOreIllegalArgumentException getError(Multiplicity multiplicity) {
        return new SiOreIllegalArgumentException(
                "badMultiplicity",
                Map.of(
                        "multiplicity", multiplicity,
                        "knownMultiplicity", Arrays.stream(Multiplicity.values()).map(Multiplicity::toString).collect(Collectors.toSet())
                )
        );
    }

    public LineCheckerWarper buildLineCheckerWarper(Application app, Configuration.CheckerDescription checkerDescription, CheckerTarget target, LineTransformer transformer, CheckerFactory checkerFactory) {
        return switch (this) {
            case MANY->LineCheckerWarper.buildMany(app, checkerDescription, target, transformer, checkerFactory);
            default->LineCheckerWarper.buildOne(app, checkerDescription, target, transformer, checkerFactory);

        };
    }*/
}