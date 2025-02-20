package fr.inra.oresing.domain.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.groovy.exception.GroovyException;
import fr.inra.oresing.domain.groovy.predefined.script.ScriptConstantProvider;

import javax.script.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public non-sealed class GroovyExpression implements Expression<Object> {

    private static final Map<String, GroovyExpression> INSTANCES = new ConcurrentHashMap<>();

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private final String expression;

    private final CompiledScript script;

    public GroovyExpression(final String expression) {
        super();
        this.expression = expression.replaceAll("\\s+", " ").trim();
        try {
            script = compile(this.expression);
        } catch (final ScriptException e) {
            throw getError(expression, e);
        }
    }

    public static SiOreIllegalArgumentException getError(final String expression, final ScriptException e) {
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

    public static SiOreIllegalArgumentException getError(final String expression, final ScriptException e, final Map<String, Object> context) {
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

    public static GroovyExpression forExpression(final String expression) {
        return INSTANCES.computeIfAbsent(expression, GroovyExpression::new);
    }

    public static Optional<CompilationError> validateExpression(final String expression) {
        try {
            compile(expression);
            return Optional.empty();
        } catch (final ScriptException e) {
            final int lineNumber = e.getLineNumber();
            final int columnNumber = e.getColumnNumber();
            final String message = e.getCause().getMessage();
            return Optional.of(new CompilationError(lineNumber, columnNumber, message));
        }
    }

    private static CompiledScript compile(final String expression) throws ScriptException {
        return ((Compilable) ENGINE).compile(expression);
    }

    @Override
    public Object evaluate(final Map<String, Object> context) {
        Map<String, Object> mutableContext = new HashMap<>(context);
        ScriptConstantProvider.addAllToContext(mutableContext);
        try {
            final Bindings bindings = new SimpleBindings();
            bindings.putAll(mutableContext);
            final Object evaluation = script.eval(bindings);

            // Vérifier si le résultat est une GroovyException
            if (evaluation instanceof GroovyException) {
                throw (GroovyException) evaluation;
            }

            return evaluation;
        } catch (ScriptException e) {
            // Vérifier si la cause originale est une GroovyException
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof GroovyException) {
                    throw (GroovyException) cause;
                }
                cause = cause.getCause();
            }

            // Si ce n'est pas une GroovyException, lancer l'erreur habituelle
            final int lineNumber = e.getLineNumber();
            final int columnNumber = e.getColumnNumber();
            throw getError(expression, e, context);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("expression", expression)
                .toString();
    }

    public record CompilationError(int lineNumber, int columnNumber, String message) {
    }
}