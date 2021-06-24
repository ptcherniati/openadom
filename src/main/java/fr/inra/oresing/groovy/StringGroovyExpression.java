package fr.inra.oresing.groovy;

import fr.inra.oresing.OreSiTechnicalException;

import java.util.Map;

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
            throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur de type chaîne de caractères mais " + evaluation + ". Expression = " + expression + ", donnée = " + context);
        }
    }
}
