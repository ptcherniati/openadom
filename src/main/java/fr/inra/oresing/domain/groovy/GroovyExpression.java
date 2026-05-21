package fr.inra.oresing.domain.groovy;

import com.google.common.base.MoreObjects;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.groovy.exception.GroovyException;
import fr.inra.oresing.domain.groovy.predefined.script.ScriptConstantProvider;

import javax.script.*;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public non-sealed class GroovyExpression implements Expression<Object> {

    private static final Map<String, GroovyExpression> INSTANCES = new ConcurrentHashMap<>();

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    // ─── R-P2-4 : sentinelle pour les résultats null dans la ConcurrentHashMap ─
    // ConcurrentHashMap interdit les valeurs null ; on utilise ce marqueur à la place.
    private static final Object NULL_SENTINEL = new Object();

    private final String expression;

    private final CompiledScript script;

    /**
     * TRANSFORM iter2 #3 : memoize isCacheable() result computed once in ctor .
     * Profile async-profiler shows {@code expression.contains("currentRowNumber")}
     * called per evaluate() on hot path ( ~75 samples on 2030 total = 3.7% CPU ) .
     * String.contains() is O(N) , for ~50-char expressions x 1M rows x N expressions
     * = wasted CPU . Boolean field = O(1) read , no allocation .
     */
    private final boolean cacheable;

    /** Pattern statique compile une seule fois pour normaliser les espaces ;
     *  utilise dans le constructeur ( pas dans le hot path par row , mais
     *  evite N compilations regex au demarrage si beaucoup d'expressions ) . */
    private static final java.util.regex.Pattern WHITESPACE_PATTERN =
            java.util.regex.Pattern.compile("\\s+");

    /**
     * TRANSFORM optim A : scratch HashMap thread-local reuse pour eviter une
     * allocation {@code new HashMap<>(context)} par {@link #evaluate} ( N par row x
     * M expressions = sources GC pressure ) . Chaque thread de la cascade
     * transform pool ( typiquement 3-8 workers ) a son propre HashMap reutilise
     * via {@code clear() + putAll(context)} a chaque appel . Aucun partage entre
     * threads = aucune contention . Capacite initiale 32 = couvre la majorite des
     * contextes ( datum + few OA_xxx closures ) sans resize .
     *
     * <p>Safety : les Closure Groovy creees par {@link ScriptConstantProvider}
     * capturent ce scratch par reference ( ex {@code context.get("datum")} dans
     * BuildCompositeKey ) . Elles s'executent SYNCHRONEMENT pendant
     * {@code script.eval(bindings)} ; apres retour de evaluate() , aucune closure
     * n'est conservee . Donc reutiliser le scratch au prochain appel est safe :
     * les anciennes closures sont devenues unreachable .
     */
    // Le scratch est intentionnellement réutilisé entre appels sur le même thread (R-P2 perf).
    // scratch.clear() est appelé au début de chaque evaluate() — la valeur est toujours fraîche.
    @SuppressWarnings("java:S5164")
    private static final ThreadLocal<java.util.HashMap<String, Object>> EVAL_SCRATCH =
            ThreadLocal.withInitial(() -> HashMap.newHashMap(32));

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

    public GroovyExpression(final String expression) {
        super();
        this.expression = WHITESPACE_PATTERN.matcher(expression).replaceAll(" ").trim();
        // TRANSFORM iter2 #3 : compute cacheable once . Same expression , same answer .
        this.cacheable = !this.expression.contains("currentRowNumber");
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
        final boolean tryCache = cacheable && !context.containsKey("currentRow");
        if (tryCache) {
            Object cached = resultCache.get(context);
            if (cached != null) {
                return cached == NULL_SENTINEL ? null : cached;
            }
        }

        // TRANSFORM optim A : reuse scratch HashMap par thread au lieu d'allouer
        // un nouveau HashMap par eval . Sur 1M rows * 5 expressions * 3 threads ,
        // economise ~15M allocations + GC pressure correspondante .
        // {@code clear() + putAll(context)} sur HashMap pre-alloue O(N) sans
        // resize tant que context.size() <= 32 ( capacite initiale ) .
        // {@code SimpleBindings(scratch)} wrappe sans copier ( cf doc JSR 223 ) .
        final java.util.HashMap<String, Object> scratch = EVAL_SCRATCH.get();
        scratch.clear();
        scratch.putAll(context);
        ScriptConstantProvider.addAllToContext(scratch);
        final Bindings bindings = new SimpleBindings(scratch);
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