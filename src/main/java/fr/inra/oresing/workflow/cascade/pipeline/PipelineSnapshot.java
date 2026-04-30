package fr.inra.oresing.workflow.cascade.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Live snapshot of a single workflow's pipeline ( source / transform /
 * sink ) consumed by the oa-live Pipeline view .
 *
 * <p>Built on the fly by {@link PipelineRegistry} from cascade 1.9.0
 * push events ( {@code SourceFetchStartEvent} , {@code SourceChunkEmittedEvent} ,
 * {@code SinkChunkAcceptedEvent} , {@code SinkChunkWrittenEvent} ,
 * {@code PoolHeartbeatEvent} , ... ) . No DB persistence ; the
 * snapshot lives only as long as the workflow is active in the
 * registry .
 */
public record PipelineSnapshot(

        UUID    correlationId,
        Instant snapshotAt,

        /** Source stage state ( workers + recent activity ) . */
        StagePool source,

        /** Transform stage state . */
        StagePool transform,

        /** Sink stage state . */
        StagePool sink,

        /** Queue between source and transform pools . */
        QueueState transformInbox,

        /** Queue between transform and sink pools . */
        QueueState sinkInbox,

        /** Total chunks the source will produce ( null when unknown ) . */
        Integer totalChunks,

        /** Sliding window of last events ( cap 30 ) for flow animation . */
        List<PipelineEvent> recentEvents,

        /** Rolling 5-second throughput per stage . */
        Throughput throughput) {

    public record StagePool(
            String  stage,                       // SOURCE | TRANSFORM | SINK
            int     parallelism,
            int     activeCount,
            long    completedTaskCount,
            List<WorkerSlot> workers) { }

    public record WorkerSlot(
            String   name,
            String   status,                     // RUNNING | IDLE | FAILED
            Integer  currentChunk,
            long     currentRecordsProcessed,
            long     currentRecordsTotal,
            Double   currentProgressPct,
            int      chunksDoneTotal,
            Long     lastDurationMs,
            Long     avgDurationMs) { }

    public record QueueState(
            int    depth,
            int    capacity,
            double saturationPct) { }

    public record PipelineEvent(
            String  kind,                        // SOURCE_EMIT | SINK_ACCEPT | SINK_WRITTEN
            int     chunkIndex,
            String  fromStage,
            String  toStage,
            Instant at) { }

    public record Throughput(
            double sourceLinesPerSec,
            double transformLinesPerSec,
            double sinkLinesPerSec) { }
}
