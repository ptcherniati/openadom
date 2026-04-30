package fr.inra.oresing.workflow.cascade.history;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-worker live state exposed by the oa-live dashboard . Aggregated from
 * {@link ChunkSnapshot} entries grouped by {@code workerName} - one row per
 * worker thread instead of one row per chunk , so the UI shows a stable
 * grid of N workers instead of a list growing unbounded with chunks .
 *
 * <p>Cascade currently only emits chunk events for the transform stage ;
 * source / sink workers are not reported per-chunk and only appear in the
 * parallelism summary alongside this list .
 */
public record WorkerSnapshot(

        /** SOURCE | TRANSFORM | SINK . */
        String   stage,

        /** Worker thread name ( e.g. {@code transform-1} ) . */
        String   name,

        /** RUNNING | IDLE | FAILED . */
        String   status,

        /** Index of the chunk currently being processed , null when IDLE . */
        Integer  currentChunk,

        /** Records already processed within the current chunk ( 0 when IDLE ) . */
        long     currentChunkRecordsProcessed,

        /** Total records expected for the current chunk ( 0 when IDLE or unknown ) . */
        long     currentChunkRecordsTotal,

        /** Convenience 0..100 progress for the current chunk ; null when unknown / IDLE . */
        Double   currentChunkProgressPercentage,

        /** Counter of chunks already handled by this worker ( includes the current one when RUNNING ) . */
        int      chunkCount,

        /** Wall-clock duration of the most recently finished chunk , null if none yet . */
        Duration lastChunkDuration,

        /** Last time this worker emitted any event ( start , progress , end ) . */
        Instant  lastActivity) {
}
