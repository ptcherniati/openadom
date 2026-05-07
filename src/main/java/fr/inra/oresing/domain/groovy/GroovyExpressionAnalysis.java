package fr.inra.oresing.domain.groovy;

import java.util.Set;

/**
 * Résultat de l'analyse statique de l'AST d'une expression Groovy.
 *
 * <p>Détermine :
 * <ul>
 *   <li>quelles colonnes du datum ({@code datum.xxx}, {@code currentRow.xxx}) sont lues</li>
 *   <li>quels référentiels ({@code references['xxx']}) sont accédés</li>
 *   <li>si la valeur spéciale {@code currentRowNumber} est utilisée</li>
 *   <li>si l'expression contient des accès dynamiques non résolvables statiquement</li>
 * </ul>
 *
 * <p>Ces informations permettent de décider si le résultat d'évaluation peut être mis en
 * cache par clé Groovy (voir {@link #isCacheable()}).
 */
public record GroovyExpressionAnalysis(
        /**
         * Noms des colonnes du datum accédées statiquement : {@code datum.site},
         * {@code currentRow.annee}, {@code datum['site']}, etc.
         */
        Set<String> datumColumns,

        /**
         * Clés de référentiels accédées statiquement : {@code references['tr_ref']},
         * {@code referencesValues['tr_ref']}, etc.
         */
        Set<String> referencesAccessed,

        /**
         * {@code true} si l'expression lit {@code currentRowNumber} —
         * valeur unique par ligne, le cache est alors inutilisable.
         */
        boolean usesCurrentRowNumber,

        /**
         * {@code true} si l'expression contient accès dynamique non résolvable :
         * {@code datum[variable]}, {@code datum."${col}"}, GString comme clé d'index...
         * Dans ce cas l'analyse ne peut pas garantir la complétude des {@code datumColumns}.
         */
        boolean hasUnresolvableAccess
) {

    /**
     * Indique si le résultat de l'expression peut être mis en cache par « clé Groovy ».
     *
     * <p>Le cache est applicable si :
     * <ul>
     *   <li>l'expression n'utilise pas {@code currentRowNumber} (valeur unique par ligne)</li>
     *   <li>tous les accès au datum sont connus statiquement (pas d'accès dynamique)</li>
     * </ul>
     */
    public boolean isCacheable() {
        return !usesCurrentRowNumber && !hasUnresolvableAccess;
    }

    /**
     * Analyse signalant que l'expression n'a pas pu être analysée (syntaxe invalide,
     * exception à l'analyse, etc.) → cache désactivé par sécurité.
     */
    public static GroovyExpressionAnalysis unanalyzable() {
        return new GroovyExpressionAnalysis(Set.of(), Set.of(), false, true);
    }

    /**
     * Analyse pour une expression qui n'accède à aucune colonne du datum.
     * Ex : expression constante {@code '42'} ou utilisant uniquement des références.
     * Cache applicable.
     */
    public static GroovyExpressionAnalysis empty() {
        return new GroovyExpressionAnalysis(Set.of(), Set.of(), false, false);
    }
}