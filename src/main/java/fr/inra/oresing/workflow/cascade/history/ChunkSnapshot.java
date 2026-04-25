package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;

/**
 * Per-chunk live state exposed by the oa-live dashboard drill-down.
 * Updated in real time from cascade {@code WorkflowListener} events
 * ( ChunkStart / ChunkProgress / ChunkEnd ) ; no DB persistence , the
 * snapshot lives only as long as the parent workflow is active in
 * {@link WorkflowActiveRegistry}.
 */
public record ChunkSnapshot(

        int     chunkIndex,

        /** RUNNING / COMPLETED / FAILED / CANCELLED. */
        String  status,

        /** Lignes deja traitees pour ce chunk. */
        long    recordsProcessed,

        /** Total de lignes attendues pour ce chunk ( recordCount cote cascade ). */
        long    recordsTotal,

        /** Thread / virtual thread qui traite le chunk ( null avant onChunkStart ). */
        String  workerName,

        /** Demarrage du chunk - null tant que le chunk n'a pas demarre. */
        Instant startTime,

        /** Fin du chunk - null tant que le chunk tourne. */
        Instant endTime,

        /** Message d'erreur si status = FAILED , sinon null. */
        String  errorMessage) {

    /** Convenience : pourcentage 0..100 ou null si recordsTotal inconnu. */
    public Double progressPercentage() {
        if (recordsTotal <= 0) return null;
        return Math.min(100.0, (recordsProcessed * 100.0) / recordsTotal);
    }

    /** Builder-ish helper used on each ChunkProgressEvent. */
    public ChunkSnapshot withProgress(long recordsProcessed) {
        return new ChunkSnapshot(
                chunkIndex, status, recordsProcessed, recordsTotal,
                workerName, startTime, endTime, errorMessage);
    }

    /** Builder-ish helper used on ChunkEndEvent. */
    public ChunkSnapshot withEnd(String status, long recordsProcessed,
                                 Instant endTime, String errorMessage) {
        return new ChunkSnapshot(
                chunkIndex, status, recordsProcessed, recordsTotal,
                workerName, startTime, endTime, errorMessage);
    }
}
