package fr.inra.oresing.domain.data.deposit.transformation;

import java.util.Map;
import java.util.Objects;

/**
 * Clé composite pour le cache de résultats d'expressions Groovy dans {@link DataValidator}.
 *
 * <p>R-P2-4 — Une expression Groovy produisant le même résultat pour les mêmes valeurs
 * d'entrée n'a pas besoin d'être réévaluée. Cette clé combine l'expression elle-même
 * avec les valeurs pertinentes du contexte d'évaluation.
 *
 * <h2>Validité du cache</h2>
 * Une expression est considérée <em>cacheable</em> si elle ne contient pas la variable
 * {@code currentRowNumber} (qui varie à chaque ligne). Cette détection est basée sur
 * une recherche de sous-chaîne ; une analyse AST complète est prévue en R-P3 si nécessaire.
 *
 * <h2>Thread-safety</h2>
 * Ce record est immuable ; {@code inputValues} est copié en profondeur par l'appelant.
 * Plusieurs workers Cascade peuvent partager le même cache {@code ConcurrentHashMap<GroovyCacheKey, Object>}.
 */
public record GroovyCacheKey(String expression, Map<String, Object> inputValues) {

    /** Marqueur spécial pour une expression qui ne peut pas être cachée. */
    public static final GroovyCacheKey NOT_CACHEABLE = null;

    /**
     * Détermine si une expression Groovy est éligible au cache.
     * <p>
     * Simple vérification par sous-chaîne : si l'expression contient {@code "currentRowNumber"},
     * elle ne peut pas être mise en cache car ce numéro change à chaque ligne.
     *
     * @param expression l'expression Groovy à analyser
     * @return {@code true} si l'expression peut être mise en cache
     */
    public static boolean isCacheable(String expression) {
        return expression != null && !expression.contains("currentRowNumber");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroovyCacheKey that)) return false;
        return Objects.equals(expression, that.expression)
                && Objects.equals(inputValues, that.inputValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, inputValues);
    }
}