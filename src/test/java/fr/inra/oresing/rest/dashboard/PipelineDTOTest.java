package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.pipeline.PipelineSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link PipelineDTO} – from(PipelineSnapshot), poolToQueueDTO.
 */
@Tag("domain.model")
@DisplayName("PipelineDTO – from() et inner classes")
class PipelineDTOTest {

    private static PipelineSnapshot buildSnapshot() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        PipelineSnapshot.StagePool source = new PipelineSnapshot.StagePool(
                "SOURCE", 2, 1, 100L, List.of(
                buildWorkerSlot("w1", "RUNNING"),
                buildWorkerSlot("w2", "IDLE")));
        PipelineSnapshot.StagePool transform = new PipelineSnapshot.StagePool(
                "TRANSFORM", 2, 0, 50L, List.of());
        PipelineSnapshot.StagePool sink = new PipelineSnapshot.StagePool(
                "SINK", 1, 0, 30L, List.of());

        PipelineSnapshot.QueueState transformQ = new PipelineSnapshot.QueueState(5, 50, 10.0);
        PipelineSnapshot.QueueState sinkQ = new PipelineSnapshot.QueueState(3, 50, 6.0);

        PipelineSnapshot.PipelineEvent event = new PipelineSnapshot.PipelineEvent(
                "SOURCE_EMIT", 1, "SOURCE", "TRANSFORM", now);

        PipelineSnapshot.Throughput throughput = new PipelineSnapshot.Throughput(100.0, 90.0, 80.0);

        return new PipelineSnapshot(id, now, source, transform, sink,
                transformQ, sinkQ, 10, List.of(event), throughput);
    }

    private static PipelineSnapshot.WorkerSlot buildWorkerSlot(String name, String status) {
        return new PipelineSnapshot.WorkerSlot(name, status, 1,
                50L, 100L, 50.0, 5, 1000L, 950L);
    }

    @Test
    @DisplayName("from(PipelineSnapshot) remplit tous les champs")
    void fromSnapshot() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        assertThat(dto).isNotNull();
        assertThat(dto.correlationId()).isEqualTo(snap.correlationId());
        assertThat(dto.snapshotAt()).isEqualTo(snap.snapshotAt());
        assertThat(dto.totalChunks()).isEqualTo(10);
        assertThat(dto.recentEvents()).hasSize(1);
        assertThat(dto.throughput()).isNotNull();
        assertThat(dto.source()).isNotNull();
        assertThat(dto.transform()).isNotNull();
        assertThat(dto.sink()).isNotNull();
    }

    @Test
    @DisplayName("from(PipelineSnapshot) source a les bons workers")
    void fromSnapshotSourceWorkers() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        assertThat(dto.source().workers()).hasSize(2);
        assertThat(dto.source().workers().get(0).name()).isEqualTo("w1");
        assertThat(dto.source().workers().get(0).status()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("from(PipelineSnapshot, null pools) → sourceInbox null")
    void fromSnapshotNullPools() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap, null, null, null);

        assertThat(dto.sourceInbox()).isNull();
        assertThat(dto.source().poolCoreSize()).isEqualTo(-1);
    }

    @Test
    @DisplayName("from(PipelineSnapshot, pools) avec PoolSnapshot réel")
    void fromSnapshotWithPools() {
        PipelineSnapshot snap = buildSnapshot();
        fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot sourcePool =
                new fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot(
                        fr.inra.oresing.workflow.cascade.config.PoolReloader.Stage.SOURCE,
                        4, 4, 2, 4, 10, 50);
        PipelineDTO dto = PipelineDTO.from(snap, sourcePool, null, null);

        assertThat(dto.sourceInbox()).isNotNull();
        assertThat(dto.sourceInbox().depth()).isEqualTo(10);
        assertThat(dto.sourceInbox().capacity()).isEqualTo(50);
        assertThat(dto.source().poolCoreSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("ThroughputDTO.from() remplit les vitesses")
    void throughputDTO() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        assertThat(dto.throughput().sourceLinesPerSec()).isEqualTo(100.0);
        assertThat(dto.throughput().transformLinesPerSec()).isEqualTo(90.0);
        assertThat(dto.throughput().sinkLinesPerSec()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("QueueDTO.from() calcule le saturationPct")
    void queueDTOSaturation() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        // transformInbox: depth=5, capacity=50 → 10%
        assertThat(dto.transformInbox().depth()).isEqualTo(5);
        assertThat(dto.transformInbox().capacity()).isEqualTo(50);
        assertThat(dto.transformInbox().saturationPct()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("EventDTO.from() remplit correctement kind, chunkIndex, etc.")
    void eventDTO() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        PipelineDTO.EventDTO event = dto.recentEvents().get(0);
        assertThat(event.kind()).isEqualTo("SOURCE_EMIT");
        assertThat(event.chunkIndex()).isEqualTo(1);
        assertThat(event.fromStage()).isEqualTo("SOURCE");
        assertThat(event.toStage()).isEqualTo("TRANSFORM");
    }

    @Test
    @DisplayName("WorkerDTO.from() remplit name, status, progress")
    void workerDTO() {
        PipelineSnapshot snap = buildSnapshot();
        PipelineDTO dto = PipelineDTO.from(snap);

        PipelineDTO.WorkerDTO worker = dto.source().workers().get(0);
        assertThat(worker.name()).isEqualTo("w1");
        assertThat(worker.status()).isEqualTo("RUNNING");
        assertThat(worker.currentProgressPct()).isEqualTo(50.0);
        assertThat(worker.chunksDoneTotal()).isEqualTo(5);
    }
}
