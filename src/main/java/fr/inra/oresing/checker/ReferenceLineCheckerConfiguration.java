package fr.inra.oresing.checker;

/**
 * Configuration pour un checker de type "Reference" qui permet de s'assurer qu'une donnée
 * est bien une valeur parmi celle du référentiel.
 */
public interface ReferenceLineCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * Le référentiel dans lequel la valeur vérifiée devra être contenu
     */
    String getRefType();

    /**
     * Si la donnée est associée à une ou plusieurs valeurs de référentiels.
     */
    Multiplicity getMultiplicity();
}
