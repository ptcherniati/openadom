package fr.inra.oresing.groovy;

import fr.inra.oresing.OreSiTechnicalException;
import lombok.Value;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GroovyExpression {

    private static final Map<String, GroovyExpression> INSTANCES = new ConcurrentHashMap<>();

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    private final CompiledScript script;

    public static GroovyExpression forExpression(String expression) {
        return INSTANCES.computeIfAbsent(expression, GroovyExpression::new);
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

    private GroovyExpression(String expression) {
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

    public Boolean evaluate(Map<String, Object> context) {
        try {
            Bindings bindings = new SimpleBindings();
            context.forEach(bindings::put);
            Object evaluation = script.eval(bindings);
            if (evaluation instanceof Boolean) {
                return (Boolean) evaluation;
            } else {
                throw new OreSiTechnicalException("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais " + evaluation + ". Expression = " + expression + ", donnée = " + context);
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
            throw new OreSiTechnicalException(MessageFormat.format("L’évaluation de l’expression a provoqué une erreur. Expression = {0}, donnée = {1}, ligne = {2}, colonne = {3}", expression, context, lineNumber, columnNumber), e);
        }
    }
}
