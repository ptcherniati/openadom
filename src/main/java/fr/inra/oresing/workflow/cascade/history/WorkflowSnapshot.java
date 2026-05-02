package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a workflow currently in progress , produced by the
 * orchestrators and consumed by the oa-live dashboard through
 * /api/dashboard/workflows/in-progress .
 *
 * <p>The snapshot mirrors most columns of {@code oa_audit.workflow_log} but
 * lives in memory only : once the workflow finishes , the entry is removed
 * from {@link WorkflowActiveRegistry} and the final row is persisted by
 * {@link WorkflowLogWriter}.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
public record WorkflowSnapshot(
        UUID correlationId,
        String workflowType,
        UUID userId,
        String userLogin,
        String applicationName,
        String dataType,
        String resourceName,
        Instant startTime,
        String status,
        long recordsProcessed,
        long recordsFailed,
        int chunksProcessed,
        Double progressPercentage,
        long bytesTotal,
        // Total estimé/comptabilisé en début de workflow ; permet à oa-live
        // de basculer la progress bar de l'état indéterminé à déterminé.
        // 0 = inconnu , l'UI doit alors fallback sur l'animation indéterminée.
        long recordsTotal,
        List<String> errors,
        /**
         * Snapshot live des chunks composant ce workflow ( pour le drill-down
         * oa-live ). Vide quand le workflow ne s'expose pas par chunks ( ex.
         * extractions ) ou tant qu'aucun ChunkStart n'est arrive. Les
         * elements sont tries par chunkIndex croissant.
         */
        List<ChunkSnapshot> chunks,
        /**
         * Vue agrégée par worker ( 1 ligne par thread ) , dérivée des chunks
         * au read-time par {@link WorkflowActiveRegistry} . Stable même
         * quand le nombre de chunks explose .
         */
        List<WorkerSnapshot> workers,
        /**
         * Parallélisme effectif par stage ( source / transform / sink ) ,
         * affiché dans le header de la vue Workers oa-live . Null pour les
         * workflows qui ne s'exposent pas par stages ( extractions ) .
         */
        ParallelismSnapshot parallelism,
        /**
         * Cascade strategy effectivement utilisee pour ce workflow
         * ( sinkStrategy / stagingStrategy / executionMode / ... ) .
         * Null pour les workflows non chunkes .
         */
        StrategySnapshot strategy,
        /**
         * Sliding window des derniers chunks ecrits par le sink ( 1000
         * max ) . Alimente la modal SINK du dashboard pour montrer la
         * liste des chunks effectivement charges en base . Vide pour
         * les workflows non chunkes ou tant qu'aucun chunk n'a ete
         * ecrit .
         */
        List<SinkChunkRecord> sinkChunks) {

    /** Convenience helper : time elapsed since start in milliseconds. */
    public long elapsedMillis(Instant now) {
        return now.toEpochMilli() - startTime.toEpochMilli();
    }

    /** Builder-ish 'with' helper for progress bumps. */
    public WorkflowSnapshot withProgress(
            long recordsProcessed,
            long recordsFailed,
            int chunksProcessed,
            Double progressPercentage,
            long bytesTotal) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Permet de mettre à jour le total une fois le comptage effectué. */
    public WorkflowSnapshot withRecordsTotal(long recordsTotal) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Remplace la liste des chunks ( utilise par le registry au moment de l'expose ). */
    public WorkflowSnapshot withChunks(List<ChunkSnapshot> chunks) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Met à jour le bloc de parallélisme effectif ( source / transform / sink ). */
    public WorkflowSnapshot withParallelism(ParallelismSnapshot parallelism) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Remplace la liste des sink chunks ( utilise par le registry au moment de l'expose ). */
    public WorkflowSnapshot withSinkChunks(List<SinkChunkRecord> sinkChunks) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Met à jour le bloc de strategy cascade ( sinkStrategy , stagingStrategy , ... ). */
    public WorkflowSnapshot withStrategy(StrategySnapshot strategy) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }

    /** Remplace la liste des workers ( utilise par le registry au moment de l'expose ). */
    public WorkflowSnapshot withWorkers(List<WorkerSnapshot> workers) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors, chunks, workers, parallelism, strategy, sinkChunks);
    }
}
