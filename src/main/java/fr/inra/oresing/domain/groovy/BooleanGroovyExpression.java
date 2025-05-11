package fr.inra.oresing.domain.groovy;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.CheckerReturnType;
import fr.inra.oresing.domain.groovy.exception.GroovyException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BooleanGroovyExpression implements Expression<Boolean> {

    private final GroovyExpression expression;
    private final Set<String> exceptionMessages;

    private BooleanGroovyExpression(final GroovyExpression expression) {
        this(expression, new HashSet<>());
    }

    private BooleanGroovyExpression(final GroovyExpression expression, Set<String> exceptionMessages) {
        super();
        this.expression = expression;
        this.exceptionMessages = exceptionMessages;
    }

    public static BooleanGroovyExpression forExpression(final String expression, Set<String> exceptionMessages) {
        return new BooleanGroovyExpression(GroovyExpression.forExpression(expression), exceptionMessages);
    }

    @Override
    public Boolean evaluate(final Map<String, Object> context) {
        try {
            final Object evaluation = expression.evaluate(context);
            return switch (evaluation) {
                case Boolean isCorrect -> {
                    if (isCorrect) {
                        yield true;
                    }
                    throw new GroovyException(GroovyException.DEFAULT_MESSAGE);
                }
                case null, default ->
                    // TODO @Lucile le résultat de l'expression n'est pas un boolean. Fait attention bon dieu!
                        throw CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.BOOLEAN));
            };
        } catch (GroovyException groovyException) {
            if (exceptionMessages.contains(groovyException.getMessage())) {
                throw new GroovyException(
                        groovyException.getMessage()
                        );
            }
            ImmutableMap<String, Object> params = ImmutableMap.<String, Object>builder()
                    .putAll(groovyException.getParams())
                    .put("expression", expression)
                    .build();
            throw new GroovyException(groovyException.getMessage(), params);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}