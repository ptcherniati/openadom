package fr.inra.oresing.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.checker.CheckerReturnType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class StringSetGroovyExpression implements Expression<Set<String>> {

    private final GroovyExpression expression;

    private StringSetGroovyExpression(GroovyExpression expression) {
        this.expression = expression;
    }

    public static StringSetGroovyExpression forExpression(String expression) {
        return new StringSetGroovyExpression(GroovyExpression.forExpression(expression));
    }

    @Override
    public Set<String> evaluate(Map<String, Object> context) {
        Object evaluation = expression.evaluate(context);
        if (evaluation == null) {
            return null;
        } else if (evaluation instanceof String) {
            return Collections.singleton((String) evaluation);
        } else if (evaluation instanceof Iterable) {
            Set<String> result = new LinkedHashSet<>();
            for (Object unknownElement : (Iterable) evaluation) {
                if (unknownElement instanceof String) {
                    result.add((String) evaluation);
                } else if (unknownElement instanceof Number) {
                    result.add(unknownElement.toString());
                } else {
                    throw CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.SET_OF_STRING, CheckerReturnType.SET_OF_NUMBER));
                }
            }
            return result;
        } else {
            throw CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.SET_OF_STRING));
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}