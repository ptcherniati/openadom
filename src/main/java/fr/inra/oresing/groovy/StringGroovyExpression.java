package fr.inra.oresing.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.checker.CheckerReturnType;

import java.util.Map;
import java.util.Set;

public class StringGroovyExpression implements Expression<String> {

    private final GroovyExpression expression;

    private StringGroovyExpression(GroovyExpression expression) {
        this.expression = expression;
    }

    public static StringGroovyExpression forExpression(String expression) {
        return new StringGroovyExpression(GroovyExpression.forExpression(expression));
    }

    @Override
    public String evaluate(Map<String, Object> context) {
        Object evaluation = expression.evaluate(context);
        if (evaluation instanceof String) {
            return (String) evaluation;
        } else if (evaluation instanceof Number) {
            return evaluation.toString();
        } else {
            throw CheckerReturnType.getError(evaluation, expression, context,  Set.of(CheckerReturnType.STRING, CheckerReturnType.NUMBER));

        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}