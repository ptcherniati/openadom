package fr.inra.oresing.domain.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.domain.checker.CheckerReturnType;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StringGroovyExpression implements Expression<String> {

    private final GroovyExpression expression;
    private final Set<String> exceptionMessages;

    private StringGroovyExpression(final GroovyExpression expression, Set<String> exceptionMessages) {
        super();
        this.expression = expression;
        this.exceptionMessages = exceptionMessages;
    }

    public static StringGroovyExpression forExpression(final String expression, Set<String> exceptionMessages) {
        return new StringGroovyExpression(GroovyExpression.forExpression(expression), exceptionMessages);
    }

    @Override
    public String evaluate(final Map<String, Object> context) {
        if (expression == null) {
            return "";
        }
        final Object evaluation = expression.evaluate(context);
        return Optional.of(evaluation)
                .map(Object::toString)
                .orElseThrow(() -> CheckerReturnType.getError(evaluation, expression, context, Set.of(CheckerReturnType.STRING, CheckerReturnType.NUMBER)));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}