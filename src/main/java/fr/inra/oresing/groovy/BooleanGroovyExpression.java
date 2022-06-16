package fr.inra.oresing.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.checker.CheckerReturnType;

import java.util.Map;
import java.util.Set;

public class BooleanGroovyExpression implements Expression<Boolean> {

    private final GroovyExpression expression;

    private BooleanGroovyExpression(GroovyExpression expression) {
        this.expression = expression;
    }

    public static BooleanGroovyExpression forExpression(String expression) {
        return new BooleanGroovyExpression(GroovyExpression.forExpression(expression));
    }

    @Override
    public Boolean evaluate(Map<String, Object> context) {
        Object evaluation = expression.evaluate(context);
        if (evaluation instanceof Boolean) {
            return (Boolean) evaluation;
        } else {
            throw CheckerReturnType.getError(evaluation, expression, context,  Set.of(CheckerReturnType.BOOLEAN));
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}