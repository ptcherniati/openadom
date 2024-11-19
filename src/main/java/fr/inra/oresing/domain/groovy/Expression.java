package fr.inra.oresing.domain.groovy;

import java.util.Map;

/**
 * Une expression qui étant donné un contexte calcule une valeur de type R
 *
 * @param <R>
 */
public sealed interface Expression<R> permits BooleanGroovyExpression, CommonExpression, GroovyExpression, StringGroovyExpression, StringSetGroovyExpression {

    R evaluate(Map<String, Object> context);
}
