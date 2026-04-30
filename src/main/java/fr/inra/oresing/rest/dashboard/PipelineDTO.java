package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.inra.oresing.workflow.cascade.pipeline.PipelineSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire format for the oa-live Pipeline view ( cascade 1.9.0 ) returned
 * by {@code GET /api/dashboard/workflows/{correlationId}/pipeline} .
 *
 * <p>Mirrors {@link PipelineSnapshot} ; durations are emitted as
 * milliseconds and timestamps as ISO-8601 strings via Jackson defaults .
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "Pipeline", description = "Live cascade pipeline state ( source / transform / sink )")
public record PipelineDTO(
        UUID correlationId,
        Instant snapshotAt,
        StageDTO source,
        StageDTO transform,
        StageDTO sink,
        QueueDTO transformInbox,
        QueueDTO sinkInbox,
        Integer totalChunks,
        List<EventDTO> recentEvents,
        ThroughputDTO throughput) {

    public static PipelineDTO from(PipelineSnapshot s) {
        return new PipelineDTO(
                s.correlationId(),
                s.snapshotAt(),
                StageDTO.from(s.source()),
                StageDTO.from(s.transform()),
                StageDTO.from(s.sink()),
                QueueDTO.from(s.transformInbox()),
                QueueDTO.from(s.sinkInbox()),
                s.totalChunks(),
                s.recentEvents().stream().map(EventDTO::from).toList(),
                ThroughputDTO.from(s.throughput()));
    }

    @Schema(name = "PipelineStage")
    public record StageDTO(
            String stage,
            int parallelism,
            int activeCount,
            long completedTaskCount,
            List<WorkerDTO> workers) {

        static StageDTO from(PipelineSnapshot.StagePool p) {
            return new StageDTO(p.stage(), p.parallelism(), p.activeCount(),
                    p.completedTaskCount(),
                    p.workers().stream().map(WorkerDTO::from).toList());
        }
    }

    @Schema(name = "PipelineWorker")
    public record WorkerDTO(
            String  name,
            String  status,
            Integer currentChunk,
            long    currentRecordsProcessed,
            long    currentRecordsTotal,
            Double  currentProgressPct,
            int     chunksDoneTotal,
            Long    lastDurationMs,
            Long    avgDurationMs) {

        static WorkerDTO from(PipelineSnapshot.WorkerSlot w) {
            return new WorkerDTO(w.name(), w.status(), w.currentChunk(),
                    w.currentRecordsProcessed(), w.currentRecordsTotal(),
                    w.currentProgressPct(), w.chunksDoneTotal(),
                    w.lastDurationMs(), w.avgDurationMs());
        }
    }

    @Schema(name = "PipelineQueue")
    public record QueueDTO(int depth, int capacity, double saturationPct) {
        static QueueDTO from(PipelineSnapshot.QueueState q) {
            return new QueueDTO(q.depth(), q.capacity(), q.saturationPct());
        }
    }

    @Schema(name = "PipelineEvent")
    public record EventDTO(String kind, int chunkIndex, String fromStage,
                           String toStage, Instant at) {
        static EventDTO from(PipelineSnapshot.PipelineEvent e) {
            return new EventDTO(e.kind(), e.chunkIndex(), e.fromStage(), e.toStage(), e.at());
        }
    }

    @Schema(name = "PipelineThroughput")
    public record ThroughputDTO(double sourceLinesPerSec,
                                double transformLinesPerSec,
                                double sinkLinesPerSec) {
        static ThroughputDTO from(PipelineSnapshot.Throughput t) {
            return new ThroughputDTO(t.sourceLinesPerSec(),
                    t.transformLinesPerSec(), t.sinkLinesPerSec());
        }
    }
}
