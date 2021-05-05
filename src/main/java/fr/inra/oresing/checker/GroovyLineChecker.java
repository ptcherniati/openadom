package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GroovyLineChecker implements LineChecker {

    public static final String NAME = "GroovyExpression";

    public static final String PARAM_EXPRESSION = "expression";

    private static final Map<String, GroovyLineChecker> INSTANCES = new ConcurrentHashMap<>();

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    private final CompiledScript script;

    public static GroovyLineChecker forExpression(String expression) {
        return INSTANCES.computeIfAbsent(expression, GroovyLineChecker::new);
    }

    public static Optional<CompilationError> validateExpression(String expression) {
        try {
            compile(expression);
            return Optional.empty();
        } catch (ScriptException e) {
            int lineNumber = e.getLineNumber();
            int columnNumber = e.getColumnNumber();
            String message = e.getCause().getMessage();
            return Optional.of(new CompilationError(lineNumber, columnNumber, message));
        }
    }

    @Value
    public static class CompilationError {
        int lineNumber;
        int columnNumber;
        String message;
    }

    private GroovyLineChecker(String expression) {
        this.expression = expression;
        try {
            this.script = compile(expression);
        } catch (ScriptException e) {
            throw new OreSiTechnicalException("impossible de compiler l’expression " + expression, e);
        }
    }

    private static CompiledScript compile(String expression) throws ScriptException {
        return ((Compilable) ENGINE).compile(expression);
    }

    @Override
    public ValidationCheckResult check(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = toMap(datum);
        try {
            Bindings bindings = new SimpleBindings();
            bindings.put("datum", datumAsMap);
            Object evaluation = script.eval(bindings);
            if (evaluation instanceof Boolean) {
                if ((Boolean) evaluation) {
                    return DefaultValidationCheckResult.success();
                } else {
                    return DefaultValidationCheckResult.error(script + " a été évalue à FAUX", ImmutableMap.of("evaluation", evaluation));
                }
            } else {
                throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais " + evaluation + ". Expression = " + script + ", donnée = " + datum);
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
