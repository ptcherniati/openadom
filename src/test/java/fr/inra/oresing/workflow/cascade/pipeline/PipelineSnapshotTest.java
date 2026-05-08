package fr.inra.oresing.workflow.cascade.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link PipelineSnapshot} et ses sous-records.
 */
@Tag("domain.model")
@DisplayName("PipelineSnapshot — snapshot live du pipeline cascade")
class PipelineSnapshotTest {

    private static final UUID   CORR_ID = UUID.randomUUID();
    private static final Instant NOW     = Instant.now();

    // ─── sous-records ────────────────────────────────────────────────────────

    @Test
    @DisplayName("StagePool : tous les champs accessibles")
    void stagePoolFields() {
        PipelineSnapshot.StagePool pool = new PipelineSnapshot.StagePool(
                "TRANSFORM", 4, 2, 100L, List.of());
        assertThat(pool.stage()).isEqualTo("TRANSFORM");
        assertThat(pool.parallelism()).isEqualTo(4);
        assertThat(pool.activeCount()).isEqualTo(2);
        assertThat(pool.completedTaskCount()).isEqualTo(100L);
        assertThat(pool.workers()).isEmpty();
    }

    @Test
    @DisplayName("WorkerSlot : tous les champs accessibles")
    void workerSlotFields() {
        PipelineSnapshot.WorkerSlot slot = new PipelineSnapshot.WorkerSlot(
                "t-1", "RUNNING", 3, 50L, 200L, 25.0, 7, 500L, 450L);
        assertThat(slot.name()).isEqualTo("t-1");
        assertThat(slot.status()).isEqualTo("RUNNING");
        assertThat(slot.currentChunk()).isEqualTo(3);
        assertThat(slot.currentRecordsProcessed()).isEqualTo(50L);
        assertThat(slot.currentRecordsTotal()).isEqualTo(200L);
        assertThat(slot.currentProgressPct()).isEqualTo(25.0);
        assertThat(slot.chunksDoneTotal()).isEqualTo(7);
        assertThat(slot.lastDurationMs()).isEqualTo(500L);
        assertThat(slot.avgDurationMs()).isEqualTo(450L);
    }

    @Test
    @DisplayName("WorkerSlot : record equality")
    void workerSlotEquality() {
        PipelineSnapshot.WorkerSlot a = new PipelineSnapshot.WorkerSlot(
                "s-1", "IDLE", null, 0L, 0L, null, 0, null, null);
        PipelineSnapshot.WorkerSlot b = new PipelineSnapshot.WorkerSlot(
                "s-1", "IDLE", null, 0L, 0L, null, 0, null, null);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("QueueState : saturationPct calculée correctement")
    void queueState() {
        PipelineSnapshot.QueueState q = new PipelineSnapshot.QueueState(25, 100, 25.0);
        assertThat(q.depth()).isEqualTo(25);
        assertThat(q.capacity()).isEqualTo(100);
        assertThat(q.saturationPct()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("PipelineEvent : tous les champs accessibles")
    void pipelineEvent() {
        PipelineSnapshot.PipelineEvent evt = new PipelineSnapshot.PipelineEvent(
                "SINK_WRITTEN", 3, "TRANSFORM", "SINK", NOW);
        assertThat(evt.kind()).isEqualTo("SINK_WRITTEN");
        assertThat(evt.chunkIndex()).isEqualTo(3);
        assertThat(evt.fromStage()).isEqualTo("TRANSFORM");
        assertThat(evt.toStage()).isEqualTo("SINK");
        assertThat(evt.at()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Throughput : tous les champs accessibles")
    void throughput() {
        PipelineSnapshot.Throughput t = new PipelineSnapshot.Throughput(1000.0, 950.0, 900.0);
        assertThat(t.sourceLinesPerSec()).isEqualTo(1000.0);
        assertThat(t.transformLinesPerSec()).isEqualTo(950.0);
        assertThat(t.sinkLinesPerSec()).isEqualTo(900.0);
    }

    // ─── PipelineSnapshot root ────────────────────────────────────────────────

    @Test
    @DisplayName("PipelineSnapshot root : tous les champs accessibles")
    void rootRecord() {
        PipelineSnapshot.StagePool source = new PipelineSnapshot.StagePool(
                "SOURCE", 1, 0, 50L, List.of());
        PipelineSnapshot.StagePool transform = new PipelineSnapshot.StagePool(
                "TRANSFORM", 4, 2, 120L, List.of());
        PipelineSnapshot.StagePool sink = new PipelineSnapshot.StagePool(
                "SINK", 2, 1, 30L, List.of());
        PipelineSnapshot.QueueState transformQ = new PipelineSnapshot.QueueState(10, 50, 20.0);
        PipelineSnapshot.QueueState sinkQ = new PipelineSnapshot.QueueState(5, 50, 10.0);
        PipelineSnapshot.Throughput throughput = new PipelineSnapshot.Throughput(500.0, 480.0, 460.0);

        PipelineSnapshot snap = new PipelineSnapshot(
                CORR_ID, NOW, source, transform, sink,
                transformQ, sinkQ, 100,
                List.of(), throughput);

        assertThat(snap.correlationId()).isEqualTo(CORR_ID);
        assertThat(snap.snapshotAt()).isEqualTo(NOW);
        assertThat(snap.source().stage()).isEqualTo("SOURCE");
        assertThat(snap.transform().parallelism()).isEqualTo(4);
        assertThat(snap.sink().activeCount()).isEqualTo(1);
        assertThat(snap.transformInbox().depth()).isEqualTo(10);
        assertThat(snap.sinkInbox().saturationPct()).isEqualTo(10.0);
        assertThat(snap.totalChunks()).isEqualTo(100);
        assertThat(snap.recentEvents()).isEmpty();
        assertThat(snap.throughput().sourceLinesPerSec()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("PipelineSnapshot : totalChunks null (inconnu) est accepté")
    void totalChunksNull() {
        PipelineSnapshot snap = new PipelineSnapshot(
                CORR_ID, NOW,
                new PipelineSnapshot.StagePool("SOURCE", 1, 0, 0L, List.of()),
                new PipelineSnapshot.StagePool("TRANSFORM", 1, 0, 0L, List.of()),
                new PipelineSnapshot.StagePool("SINK", 1, 0, 0L, List.of()),
                new PipelineSnapshot.QueueState(0, 50, 0.0),
                new PipelineSnapshot.QueueState(0, 50, 0.0),
                null, List.of(), null);

        assertThat(snap.totalChunks()).isNull();
        assertThat(snap.throughput()).isNull();
    }
}
