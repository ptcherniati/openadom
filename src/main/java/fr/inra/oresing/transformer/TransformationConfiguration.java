package fr.inra.oresing.transformer;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.util.Streams;

import java.util.Set;

/**
 * Indique qu'il faut transformer la donnée (avant de la vérifier) et comment
 */
public interface TransformationConfiguration {

    /**
     * Si la valeur doit être transformée en l'échappant pour lui donner la forme d'une clé
     */
    boolean isCodify();

    /**
     * La valeur doit être transformer en appliquant cette expression Groovy
     */
    String getGroovy();

    /**
     * Ensemble des référentiels qu'il faut charger dans le contexte pour l'évaluation de l'expression {@link #getGroovy()}
     */
    String getReferences();

    default Set<String> doGetReferencesAsCollection() {
        return Streams.stream(Splitter.on(",").split(getReferences())).collect(ImmutableSet.toImmutableSet());
    }
}
