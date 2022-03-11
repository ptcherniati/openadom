package fr.inra.oresing.checker;

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
    MANY
}
