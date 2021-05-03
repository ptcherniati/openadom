package fr.inra.oresing.checker;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.LinkedHashMap;
import java.util.Map;

public class GroovyLineChecker {

    private final ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    public GroovyLineChecker(String expression) {
        this.expression = expression;
    }

    public ValidationCheckResult check(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = toMap(datum);
        try {
            engine.put("datum", datumAsMap);
            Object evaluation = engine.eval(expression);
            if (evaluation instanceof Boolean) {
                if ((Boolean) evaluation) {
                    return new ValidationCheckResult(true, null, null);
                } else {
                    return new ValidationCheckResult(false, "", null);
                }
            } else {
                throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais " + evaluation + ". Expression = " + expression + ", donnée = " + datum);
            }
        } catch (ScriptException e) {
//            int lineNumber = e.getLineNumber();
//            int columnNumber = e.getColumnNumber();
//            if (e.getCause() instanceof MultipleCompilationErrorsException) {
//
//            }
//            if (e.getCause() instanceof GroovyRuntimeException) {
//
//            }
            throw new OreSiTechnicalException("L'évaluation de l’expression a provoqué une erreur. Expression = " + expression + ", donnée = " + datum, e);
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
