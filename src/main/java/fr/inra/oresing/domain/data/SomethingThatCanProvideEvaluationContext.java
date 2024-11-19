package fr.inra.oresing.domain.data;

import com.google.common.collect.ImmutableMap;

/**
 * Désigne un objet qui a vocation à être exposé dans un contexte Groovy.
 * <p>
 * On doit donc pouvoir le transformer dans un objet qui soit exploitable
 * dans le code Groovy qui va être écrit et donc mettre les chose à plat
 * avec des objets simples.
 */
public interface SomethingThatCanProvideEvaluationContext {

    /**
     * Récupérer le contenu de cet objet sous forme de Map qui peut être lue en groovy.
     * @return
     */
    ImmutableMap<String, Object> getEvaluationContext();
}
