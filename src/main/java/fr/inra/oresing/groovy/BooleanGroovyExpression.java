package fr.inra.oresing.groovy;

import fr.inra.oresing.OreSiTechnicalException;

import java.util.Map;

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
            throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais " + evaluation + ". Expression = " + expression + ", donnée = " + context);
        }
    }
}
