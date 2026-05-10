package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les méthodes {@code with*()} de {@link WorkflowSnapshot}
 * non encore couvertes par WorkflowHistoryRecordsTest.
 *
 * <p>Toutes les méthodes retournent un nouveau snapshot (immutabilité).
 */
@Tag("domain.model")
@DisplayName("WorkflowSnapshot — méthodes with* et records associés")
class WorkflowSnapshotWithMethodsTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW  = Instant.now();

    private WorkflowSnapshot base() {
        return WorkflowSnapshot.minimal(
                CORR_ID, "IMPORT", USER_ID, "alice",
                "app1", "data", "file.csv", NOW,
                "IN_PROGRESS", 0L, 0L, 0, null, 0L, 0L,
                List.of(), null);
    }

    // ─── withWorkers() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("withWorkers() met à jour la liste des workers")
    void withWorkers() {
        WorkerSnapshot worker = new WorkerSnapshot(
                "TRANSFORM", "t-1", "RUNNING", 0, 50L, 100L, 50.0,
                1, Duration.ofMillis(200L), Duration.ofMillis(200L), NOW);

        WorkflowSnapshot updated = base().withWorkers(List.of(worker));

        assertThat(updated.workers()).hasSize(1);
        assertThat(updated.workers().get(0).name()).isEqualTo("t-1");
        // snapshot original non muté — workers = List.of() (vide, pas null)
        assertThat(base().workers()).isEmpty();
    }

    @Test
    @DisplayName("withWorkers() retourne un nouveau snapshot")
    void withWorkersImmutability() {
        WorkflowSnapshot snap  = base();
        WorkflowSnapshot updated = snap.withWorkers(List.of());
        assertThat(snap).isNotSameAs(updated);
    }

    // ─── withParallelism() ───────────────────────────────────────────────────

    @Test
    @DisplayName("withParallelism() met à jour le bloc de parallélisme")
    void withParallelism() {
        ParallelismSnapshot p = new ParallelismSnapshot(1, 4, 2);
        WorkflowSnapshot updated = base().withParallelism(p);

        assertThat(updated.parallelism()).isNotNull();
        assertThat(updated.parallelism().transform()).isEqualTo(4);
        assertThat(base().parallelism()).isNull();
    }

    @Test
    @DisplayName("ParallelismSnapshot.empty() → (0,0,0)")
    void parallelismEmpty() {
        ParallelismSnapshot empty = ParallelismSnapshot.empty();
        assertThat(empty.source()).isEqualTo(0);
        assertThat(empty.transform()).isEqualTo(0);
        assertThat(empty.sink()).isEqualTo(0);
    }

    // ─── withStrategy() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("withStrategy() met à jour le bloc strategy")
    void withStrategy() {
        StrategySnapshot s = new StrategySnapshot("MERGE_FILE", null, "STAGED", 1);
        WorkflowSnapshot updated = base().withStrategy(s);

        assertThat(updated.strategy()).isNotNull();
        assertThat(updated.strategy().sinkStrategy()).isEqualTo("MERGE_FILE");
        assertThat(base().strategy()).isNull();
    }

    @Test
    @DisplayName("StrategySnapshot : tous les champs accessibles")
    void strategySnapshotFields() {
        StrategySnapshot ss = new StrategySnapshot("DIRECT_COPY", "SHARED_UNLOGGED", "PIPELINED", 2);
        assertThat(ss.sinkStrategy()).isEqualTo("DIRECT_COPY");
        assertThat(ss.stagingStrategy()).isEqualTo("SHARED_UNLOGGED");
        assertThat(ss.pipelineMode()).isEqualTo("PIPELINED");
        assertThat(ss.sinkParallelism()).isEqualTo(2);
    }

    // ─── withSinkChunks() ────────────────────────────────────────────────────

    @Test
    @DisplayName("withSinkChunks() met à jour la liste des sink chunks")
    void withSinkChunks() {
        SinkChunkRecord sc = new SinkChunkRecord(0, "sink-1", "COMPLETED", 150L, NOW, null);
        WorkflowSnapshot updated = base().withSinkChunks(List.of(sc));

        assertThat(updated.sinkChunks()).hasSize(1);
        assertThat(updated.sinkChunks().get(0).workerName()).isEqualTo("sink-1");
        assertThat(base().sinkChunks()).isEmpty();
    }

    @Test
    @DisplayName("SinkChunkRecord : tous les champs accessibles")
    void sinkChunkRecordFields() {
        SinkChunkRecord r = new SinkChunkRecord(3, "sink-2", "FAILED", 200L, NOW, "write error");
        assertThat(r.chunkIndex()).isEqualTo(3);
        assertThat(r.workerName()).isEqualTo("sink-2");
        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.durationMs()).isEqualTo(200L);
        assertThat(r.at()).isEqualTo(NOW);
        assertThat(r.errorMessage()).isEqualTo("write error");
    }

    // ─── withImportConfig() ──────────────────────────────────────────────────

    @Test
    @DisplayName("withImportConfig() met à jour la config d'import")
    void withImportConfig() {
        ImportConfigSnapshot ic = new ImportConfigSnapshot(
                1000, 50, 100, 0, 60, "stg_shared", true, false, 1, 4, 2, 0);
        WorkflowSnapshot updated = base().withImportConfig(ic);

        assertThat(updated.importConfig()).isNotNull();
        assertThat(updated.importConfig().chunkSizeLines()).isEqualTo(1000);
        assertThat(base().importConfig()).isNull();
    }

    @Test
    @DisplayName("ImportConfigSnapshot : tous les champs accessibles")
    void importConfigSnapshotFields() {
        ImportConfigSnapshot ic = new ImportConfigSnapshot(
                500, 25, 200, 10, 30, "stg_test", false, true, 2, 8, 4, 1);
        assertThat(ic.chunkSizeLines()).isEqualTo(500);
        assertThat(ic.progressBatchSize()).isEqualTo(25);
        assertThat(ic.maxErrorsThreshold()).isEqualTo(200);
        assertThat(ic.collectorChunkSize()).isEqualTo(10);
        assertThat(ic.stagingSharedOrphanTtl()).isEqualTo(30);
        assertThat(ic.stagingSharedTableName()).isEqualTo("stg_test");
        assertThat(ic.enableMetrics()).isFalse();
        assertThat(ic.skipCsvReencoding()).isTrue();
        assertThat(ic.poolSource()).isEqualTo(2);
        assertThat(ic.poolTransform()).isEqualTo(8);
        assertThat(ic.poolSink()).isEqualTo(4);
        assertThat(ic.poolOrdering()).isEqualTo(1);
    }

    @Test
    @DisplayName("ImportConfigSnapshot : record equality")
    void importConfigSnapshotEquality() {
        ImportConfigSnapshot a = new ImportConfigSnapshot(1000, 50, 100, 0, 60, "stg", true, false, 1, 4, 2, 0);
        ImportConfigSnapshot b = new ImportConfigSnapshot(1000, 50, 100, 0, 60, "stg", true, false, 1, 4, 2, 0);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // ─── withLastHeartbeatAt() ────────────────────────────────────────────────

    @Test
    @DisplayName("withLastHeartbeatAt() met à jour le timestamp heartbeat")
    void withLastHeartbeatAt() {
        Instant heartbeat = NOW.minus(Duration.ofSeconds(30));
        WorkflowSnapshot updated = base().withLastHeartbeatAt(heartbeat);

        assertThat(updated.lastHeartbeatAt()).isEqualTo(heartbeat);
        assertThat(base().lastHeartbeatAt()).isNull();
    }

    // ─── chaîne complète ─────────────────────────────────────────────────────

    @Test
    @DisplayName("chaîne with* : original non muté après plusieurs mises à jour")
    void chainWithMethodsImmutability() {
        WorkflowSnapshot original = base();

        WorkflowSnapshot enriched = original
                .withWorkers(List.of())
                .withParallelism(new ParallelismSnapshot(1, 4, 2))
                .withStrategy(new StrategySnapshot("MERGE_FILE", null, "STAGED", 1))
                .withImportConfig(new ImportConfigSnapshot(
                        1000, 50, 100, 0, 60, "stg", true, false, 1, 4, 2, 0))
                .withLastHeartbeatAt(NOW);

        // original reste intact
        assertThat(original.workers()).isEmpty(); // List.of() par défaut
        assertThat(original.parallelism()).isNull();
        assertThat(original.strategy()).isNull();
        assertThat(original.importConfig()).isNull();
        assertThat(original.lastHeartbeatAt()).isNull();

        // enriched a tout
        assertThat(enriched.parallelism().transform()).isEqualTo(4);
        assertThat(enriched.strategy().sinkStrategy()).isEqualTo("MERGE_FILE");
        assertThat(enriched.importConfig().chunkSizeLines()).isEqualTo(1000);
        assertThat(enriched.lastHeartbeatAt()).isEqualTo(NOW);
    }

    // ─── WorkerSnapshot record ────────────────────────────────────────────────

    @Test
    @DisplayName("WorkerSnapshot : tous les champs accessibles")
    void workerSnapshotFields() {
        WorkerSnapshot ws = new WorkerSnapshot(
                "SINK", "sink-1", "IDLE", null, 0L, 0L, null, 3,
                Duration.ofMillis(500), Duration.ofMillis(400), NOW);
        assertThat(ws.stage()).isEqualTo("SINK");
        assertThat(ws.name()).isEqualTo("sink-1");
        assertThat(ws.status()).isEqualTo("IDLE");
        assertThat(ws.currentChunk()).isNull();
        assertThat(ws.chunkCount()).isEqualTo(3);
        assertThat(ws.lastChunkDuration()).isEqualTo(Duration.ofMillis(500));
        assertThat(ws.avgChunkDuration()).isEqualTo(Duration.ofMillis(400));
        assertThat(ws.lastActivity()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("WorkerSnapshot : record equality")
    void workerSnapshotEquality() {
        WorkerSnapshot a = new WorkerSnapshot("SOURCE", "s-1", "RUNNING", 0, 0L, 100L,
                0.0, 0, null, null, NOW);
        WorkerSnapshot b = new WorkerSnapshot("SOURCE", "s-1", "RUNNING", 0, 0L, 100L,
                0.0, 0, null, null, NOW);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
