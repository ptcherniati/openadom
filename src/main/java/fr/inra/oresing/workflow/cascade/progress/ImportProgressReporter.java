package fr.inra.oresing.workflow.cascade.progress;

/**
 * Callback de progression pendant le traitement d'un chunk d'import CSV.
 * Remplace l'ancienne dependance a
 * {@code WorkflowLifecycleManager.incrementProcessedLines} et
 * {@code SharedContext.incrementProcessedLines} du JAR file-processor.
 *
 * <p>Méthodes :
 * <ul>
 *   <li>{@link #onTotalLinesKnown} — appelée une seule fois après le comptage
 *       du fichier, quand le nombre total de lignes est connu.
 *   <li>{@link #onLinesProcessed} — appelée à chaque batch de validation.
 * </ul>
 * Les implementations peuvent brancher Micrometer, un store DB, un EventPublisher, etc.
 */
public interface ImportProgressReporter {

    /**
     * Notifié une seule fois, en début de workflow, lorsque le nombre total
     * de lignes de données (hors en-tête) est connu.
     * Implémentation par défaut : no-op.
     */
    default void onTotalLinesKnown(String correlationId, long totalLines) {
        // no-op par défaut
    }

    /**
     * Notifié lorsque {@code delta} lignes supplementaires ont ete traitées
     * avec succes pour le workflow {@code correlationId}.
     */
    void onLinesProcessed(String correlationId, int delta);
}