package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a workflow currently in progress , produced by the
 * orchestrators and consumed by the oa-live dashboard through
 * /api/dashboard/workflows/in-progress .
 *
 * <p>The snapshot mirrors most columns of {@code oa_metrics.workflow_log} but
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
        List<String> errors) {

    /** Convenience helper : time elapsed since start in milliseconds. */
    public long elapsedMillis(Instant now) {
        return now.toEpochMilli() - startTime.toEpochMilli();
    }

    /** Builder-ish ‘with’ helper for progress bumps. */
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
                progressPercentage, bytesTotal, recordsTotal, errors);
    }

    /** Permet de mettre à jour le total une fois le comptage effectué. */
    public WorkflowSnapshot withRecordsTotal(long recordsTotal) {
        return new WorkflowSnapshot(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName, startTime,
                status, recordsProcessed, recordsFailed, chunksProcessed,
                progressPercentage, bytesTotal, recordsTotal, errors);
    }
}
