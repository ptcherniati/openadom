package fr.inra.oresing.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import lombok.Value;

import javax.script.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GroovyExpression implements Expression<Object> {

    private static final Map<String, GroovyExpression> INSTANCES = new ConcurrentHashMap<>();

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    private final CompiledScript script;

    public static SiOreIllegalArgumentException getError(String expression, ScriptException e) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionChecker",
                Map.of(
                        "expression", expression,
                        "lineNumber", e.getLineNumber(),
                        "columnNumber", e.getColumnNumber(),
                        "message", e.getLocalizedMessage()
                )
        );
    }
    public static SiOreIllegalArgumentException getError(String expression, ScriptException e, Map<String, Object> context) {
        return new SiOreIllegalArgumentException(
                "badGroovyExpressionChecker",
                Map.of(
                        "expression", expression,
                        "lineNumber", e.getLineNumber(),
                        "columnNumber", e.getColumnNumber(),
                        "context", context,
                        "message", e.getLocalizedMessage()
                )
        );
    }

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
            throw GroovyExpression.getError(expression, e);
        }
    }

    private static CompiledScript compile(String expression) throws ScriptException {
        return ((Compilable) ENGINE).compile(expression);
    }

    @Override
    public Object evaluate(Map<String, Object> context) {
        try {
            Bindings bindings = new SimpleBindings();
            context.forEach(bindings::put);
            Object evaluation = script.eval(bindings);
            return evaluation;
        } catch (ScriptException e) {
            int lineNumber = e.getLineNumber();
            int columnNumber = e.getColumnNumber();
//            if (e.getCause() instanceof MultipleCompilationErrorsException) {
//
//            }
//            if (e.getCause() instanceof GroovyRuntimeException) {
//
//            }
            throw GroovyExpression.getError(expression, e, context);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }
}