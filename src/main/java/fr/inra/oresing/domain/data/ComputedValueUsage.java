package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exprime s'il est possible de calculer une valeur pour une donnée et si oui, qu'en faire.
 */
public enum ComputedValueUsage {

    NOT_COMPUTED,

    USE_COMPUTED_AS_DEFAULT_VALUE,

    USE_COMPUTED_VALUE;


    public static SiOreIllegalArgumentException getError(final ComputedValueUsage computedValueUsage) {
        return new SiOreIllegalArgumentException(
                "badComputedValueUsage",
                Map.of(
                        "computedValueUsage", computedValueUsage,
                        "knownComputedValueUsage", Arrays.stream(values()).map(ComputedValueUsage::toString).collect(Collectors.toSet())
                )
        );
    }
}