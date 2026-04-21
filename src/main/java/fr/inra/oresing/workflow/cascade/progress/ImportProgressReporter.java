package fr.inra.oresing.workflow.cascade.progress;

/**
 * Callback de progression pendant le traitement d'un chunk d'import CSV.
 * Remplace l'ancienne dependance a
 * {@code WorkflowLifecycleManager.incrementProcessedLines} et
 * {@code SharedContext.incrementProcessedLines} du JAR file-processor.
 *
 * <p>Une seule responsabilité : recevoir un delta de lignes traitées avec
 * succes et le republier ou logger. Les implementations peuvent brancher
 * Micrometer, un store DB, un EventPublisher, etc.
 */
@FunctionalInterface
public interface ImportProgressReporter {

    /**
     * Notifié lorsque {@code delta} lignes supplementaires ont ete traitées
     * avec succes pour le workflow {@code correlationId}.
     */
    void onLinesProcessed(String correlationId, int delta);
}
