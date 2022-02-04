package fr.inra.oresing.checker;

import fr.inra.oresing.transformer.TransformationConfiguration;

public interface LineCheckerConfiguration extends TransformationConfiguration {

    /**
     * Indique la valeur est obligatoire.
     */
    boolean isRequired();
}
