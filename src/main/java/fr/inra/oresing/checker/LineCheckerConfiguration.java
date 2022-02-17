package fr.inra.oresing.checker;

import fr.inra.oresing.transformer.TransformationConfiguration;

/**
 * Indique qu'un objet a vocation à contenir des paramètres de configuration pour configurer un {@link LineChecker}
 */
public interface LineCheckerConfiguration extends TransformationConfiguration {

    /**
     * Indique la valeur est obligatoire.
     */
    boolean isRequired();
}
