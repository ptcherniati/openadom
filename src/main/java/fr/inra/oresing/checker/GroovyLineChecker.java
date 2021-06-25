package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.groovy.BooleanGroovyExpression;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class GroovyLineChecker implements LineChecker {

    public static final String NAME = "GroovyExpression";

    public static final String PARAM_EXPRESSION = "expression";

    private final BooleanGroovyExpression expression;

    public static GroovyLineChecker forExpression(String expression) {
        BooleanGroovyExpression groovyExpression = BooleanGroovyExpression.forExpression(expression);
        return new GroovyLineChecker(groovyExpression);
    }

    public static Optional<GroovyExpression.CompilationError> validateExpression(String expression) {
        return GroovyExpression.validateExpression(expression);
    }

    private GroovyLineChecker(BooleanGroovyExpression expression) {
        this.expression = expression;
    }

    @Override
    public ValidationCheckResult check(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = new LinkedHashMap<>();
        for (Map.Entry<VariableComponentKey, String> entry2 : datum.entrySet()) {
            String variable = entry2.getKey().getVariable();
            String component = entry2.getKey().getComponent();
            String value = entry2.getValue();
            datumAsMap.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
        }
        Map<String, Object> context = ImmutableMap.of("datum", datumAsMap);
        Boolean evaluation = expression.evaluate(context);
        if (evaluation) {
            return DefaultValidationCheckResult.success();
        } else {
            return DefaultValidationCheckResult.error("checkerExpressionReturnedFalse", ImmutableMap.of("expression", expression));
        }
    }
}
