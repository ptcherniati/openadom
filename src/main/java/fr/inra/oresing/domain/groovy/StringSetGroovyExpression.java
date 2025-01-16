package fr.inra.oresing.domain.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.domain.checker.CheckerReturnType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StringSetGroovyExpression implements Expression<Set<String>> {

    private final GroovyExpression expression;

    private StringSetGroovyExpression(final GroovyExpression expression) {
        super();
        this.expression = expression;
    }

    public static StringSetGroovyExpression forExpression(final String expression) {
        return new StringSetGroovyExpression(GroovyExpression.forExpression(expression));
    }

    @Override
    public Set<String> evaluate(final Map<String, Object> context) {
        final Object evaluation = expression.evaluate(context);
        switch (evaluation) {
            case null -> {
                return null;
            }
            case String s -> {
                return Collections.singleton(s);
            }
            case Iterable iterable -> {
                final Set<String> result = new LinkedHashSet<>();
                for (final Object unknownElement : iterable) {
                    switch (unknownElement) {
                        case final String ignored -> result.add((String) evaluation);
                        case final Number ignored -> result.add(unknownElement.toString());
                        case null, default ->
                                throw CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.SET_OF_STRING, CheckerReturnType.SET_OF_NUMBER));
                    }
                }
                return result;
            }
            default -> {
            }
        }
        throw CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.SET_OF_STRING));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}