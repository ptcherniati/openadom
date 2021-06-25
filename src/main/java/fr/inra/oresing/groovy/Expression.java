package fr.inra.oresing.groovy;

import java.util.Map;

/**
 * Une expression qui étant donné un contexte calcule une valeur de type R
 *
 * @param <R>
 */
public interface Expression<R> {

    R evaluate(Map<String, Object> context);
}
