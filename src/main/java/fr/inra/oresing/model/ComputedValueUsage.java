package fr.inra.oresing.model;

/**
 * Exprime s'il est possible de calculer une valeur pour une donnée et si oui, qu'en faire.
 */
public enum ComputedValueUsage {

    NOT_COMPUTED,

    USE_COMPUTED_AS_DEFAULT_VALUE,

    USE_COMPUTED_VALUE;

}
