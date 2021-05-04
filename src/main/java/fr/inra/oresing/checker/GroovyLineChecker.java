package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroovyLineChecker implements LineChecker {

    public static final String PARAM_EXPRESSION = "expression";

    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    private static final Map<String, GroovyLineChecker> instances = new ConcurrentHashMap<>();

    public static GroovyLineChecker forExpression(String expression) {
        return instances.computeIfAbsent(expression, GroovyLineChecker::new);
    }

    private GroovyLineChecker(String expression) {
        this.expression = expression;
    }

    @Override
    public ValidationCheckResult check(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = toMap(datum);
        try {
            engine.put("datum", datumAsMap);
            Object evaluation = engine.eval(expression);
            if (evaluation instanceof Boolean) {
                if ((Boolean) evaluation) {
                    return new DefaultValidationCheckResult(true, null, null);
                } else {
                    return new DefaultValidationCheckResult(false, expression + " a été évalue à FAUX", ImmutableMap.of("evaluation", evaluation));
                }
            } else {
                throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais " + evaluation + ". Expression = " + expression + ", donnée = " + datum);
            }
        } catch (ScriptException e) {
            int lineNumber = e.getLineNumber();
            int columnNumber = e.getColumnNumber();
//            if (e.getCause() instanceof MultipleCompilationErrorsException) {
//
//            }
//            if (e.getCause() instanceof GroovyRuntimeException) {
//
//            }
            throw new OreSiTechnicalException(MessageFormat.format("L’évaluation de l’expression a provoqué une erreur. Expression = {0}, donnée = {1}, ligne = {2}, colonne = {3}", expression, datum, lineNumber, columnNumber), e);
        }
    }

    private Map<String, Map<String, String>> toMap(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = new LinkedHashMap<>();
        for (Map.Entry<VariableComponentKey, String> entry2 : datum.entrySet()) {
            String variable = entry2.getKey().getVariable();
            String component = entry2.getKey().getComponent();
            String value = entry2.getValue();
            datumAsMap.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
        }
        return datumAsMap;
    }
}
