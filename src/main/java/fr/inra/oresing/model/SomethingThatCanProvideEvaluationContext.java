package fr.inra.oresing.model;

import com.google.common.collect.ImmutableMap;

/**
 * Désigne un objet qui a vocation à être exposé dans un contexte Groovy.
 *
 * On doit donc pouvoir le transformer dans un objet qui soit exploitable
 * dans le code Groovy qui va être écrit et donc mettre les chose à plat
 * avec des objets simples.
 */
public interface SomethingThatCanProvideEvaluationContext {

    /**
     * Récupérer le contenu de cet objet sous forme de Map qui peut être lue en groovy.
     */
    ImmutableMap<String, Object> getEvaluationContext();
}
