package fr.inra.oresing.checker;

/**
 * Configuration pour un checker de type expression régulière
 */
public interface RegularExpressionCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * L'expression régulière à laquelle doit être conforme la valeur qui sera vérifiée.
     */
    String getPattern();
}
