package fr.inra.oresing.transformer;

import fr.inra.oresing.checker.GroovyConfiguration;

/**
 * Indique qu'il faut transformer la donnée (avant de la vérifier) et comment
 */
public interface TransformationConfiguration {

    /**
     * Si la valeur doit être transformée en l'échappant pour lui donner la forme d'une clé
     */
    boolean isCodify();

    /**
     * Avant d'être vérifiée, la donnée doit être transformée en appliquant cette expression.
     */
    GroovyConfiguration getGroovy();

}
