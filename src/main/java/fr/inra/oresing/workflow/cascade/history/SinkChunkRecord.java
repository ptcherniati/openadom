package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;

/**
 * Per-chunk record of a sink-stage write , kept in a sliding window
 * by {@link WorkflowActiveRegistry} so the oa-live SINK drill-down
 * modal can list every chunk loaded into the database without
 * relying on transform-side {@link ChunkSnapshot} entries
 * ( cascade emits those for the transform stage only ) .
 *
 * <p>Sliding window cap : 1000 entries per workflow . Older records
 * are evicted FIFO ; for the dashboard use case ( drill-down on a
 * running or just-finished workflow ) that is plenty .
 *
 * @param chunkIndex   index attributed by the source ( same as transform / sink )
 * @param workerName   thread that performed the sink write
 * @param status       SUCCESS | FAILED | CANCELLED
 * @param durationMs   wall-clock duration of the {@code Sink.write()} call
 * @param at           end-of-write timestamp
 * @param errorMessage null on success
 *
 * @since 1.9.2
 */
public record SinkChunkRecord(
        int     chunkIndex,
        String  workerName,
        String  status,
        long    durationMs,
        Instant at,
        String  errorMessage) {
}
