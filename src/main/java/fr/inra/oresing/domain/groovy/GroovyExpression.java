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

    // ─── R-P2-4 : sentinelle pour les résultats null dans la ConcurrentHashMap ─
    // ConcurrentHashMap interdit les valeurs null ; on utilise ce marqueur à la place.
    private static final Object NULL_SENTINEL = new Object();

    private final String expression;

    private final CompiledScript script;

    // ─── R-P2-4 : cache des résultats d'évaluation Groovy ───────────────────
    // Clé : contexte d'entrée (Map<String,Object>) — même entrée → même sortie
    // pour les expressions sans effets de bord. Les expressions utilisant
    // currentRowNumber sont exclues du cache (via isCacheable).
    // Bounded par maxCacheEntries pour limiter la pression mémoire par import.
    private final ConcurrentHashMap<Map<String, Object>, Object> resultCache =
            new ConcurrentHashMap<>();
    private volatile int maxCacheEntries = 1_000;

    /**
     * Configure le plafond du cache de résultats (R-P2-4).
     * Appelé depuis DataImporter après lecture de {@code ImportProperties}.
     */
    public void setMaxCacheEntries(int max) {
        this.maxCacheEntries = max;
    }

    /**
     * R-P2-4 — Une expression est cacheable si elle ne contient pas
     * {@code currentRowNumber} (variable qui change à chaque ligne).
     */
    private static boolean isCacheable(String expression) {
        return !expression.contains("currentRowNumber");
    }

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
        // R-P2-4 : consulter le cache uniquement si l'expression est statique
        // ET si le contexte ne contient pas "currentRow" (présent dans TOUS les
        // contextes de validation de données — change à chaque ligne).
        // Sans ce guard, on paierait hashCode(context) + Map.copyOf(context)
        // à chaque évaluation sans jamais toucher le cache → régression pure.
        final boolean tryCache = isCacheable(expression) && !context.containsKey("currentRow");
        if (tryCache) {
            Object cached = resultCache.get(context);
            if (cached != null) {
                return cached == NULL_SENTINEL ? null : cached;
            }
        }

        // R-P2-5 : fusionne le contexte + les constantes en une seule copie HashMap
        // (avant : new HashMap<>(context) + putAll dans SimpleBindings = 2 copies).
        final Bindings bindings = new SimpleBindings(new HashMap<>(context));
        ScriptConstantProvider.addAllToContext(bindings);
        try {
            final Object evaluation = script.eval(bindings);

            // Vérifier si le résultat est une GroovyException
            if (evaluation instanceof GroovyException) {
                throw (GroovyException) evaluation;
            }

            // R-P2-4 : stocker dans le cache si le contexte est purement statique
            if (tryCache && resultCache.size() < maxCacheEntries) {
                resultCache.put(Map.copyOf(context), evaluation != null ? evaluation : NULL_SENTINEL);
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

    public String getExpression() {
        return expression;
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