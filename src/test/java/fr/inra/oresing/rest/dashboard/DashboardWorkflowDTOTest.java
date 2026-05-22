package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.history.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link DashboardWorkflowDTO} — méthodes factory
 * {@code fromSnapshot} et classes imbriquées.
 */
@Tag("domain.model")
@DisplayName("DashboardWorkflowDTO — factory methods et inner records")
class DashboardWorkflowDTOTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW  = Instant.now();

    private static WorkflowSnapshot minimalSnapshot() {
        return WorkflowSnapshot.minimal(
                CORR_ID, "IMPORT", USER_ID, "alice",
                "app1", "data", "file.csv",
                NOW, "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L,
                List.of(), List.of());
    }

    // ─── fromSnapshot — cas minimal (toutes les listes null/vide) ─────────────

    @Test
    @DisplayName("fromSnapshot() avec snapshot minimal produit un DTO cohérent")
    void fromSnapshot_minimal() {
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());

        assertThat(dto.correlationId()).isEqualTo(CORR_ID);
        assertThat(dto.workflowType()).isEqualTo("IMPORT");
        assertThat(dto.userId()).isEqualTo(USER_ID);
        assertThat(dto.userLogin()).isEqualTo("alice");
        assertThat(dto.applicationName()).isEqualTo("app1");
        assertThat(dto.status()).isEqualTo("IN_PROGRESS");
        assertThat(dto.chunks()).isEmpty();
        assertThat(dto.workers()).isEmpty();
        assertThat(dto.parallelism()).isNull();
        assertThat(dto.strategy()).isNull();
        assertThat(dto.sinkChunks()).isEmpty();
        assertThat(dto.importConfig()).isNull();
    }

    // ─── fromSnapshot — chunks non-null ────────────────────────────────────────

    @Test
    @DisplayName("fromSnapshot() avec chunks non-null mappe les ChunkDTO")
    void fromSnapshot_withChunks() {
        ChunkSnapshot c = new ChunkSnapshot(0, "RUNNING", 10L, 100L, "transform-1",
                NOW, null, null);
        WorkflowSnapshot snap = new WorkflowSnapshot(
                CORR_ID, "IMPORT", USER_ID, "alice", "app1", "data", "file.csv",
                NOW, "IN_PROGRESS", 10L, 0L, 1, 10.0, 0L, 100L,
                List.of(), List.of(c), List.of(), null, null, List.of(), null, null);

        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);

        assertThat(dto.chunks()).hasSize(1);
        assertThat(dto.chunks().get(0).chunkIndex()).isZero();
        assertThat(dto.chunks().get(0).status()).isEqualTo("RUNNING");
    }

    // ─── fromSnapshot — workers non-null ────────────────────────────────────────

    @Test
    @DisplayName("fromSnapshot() avec workers non-null mappe les WorkerDTO")
    void fromSnapshot_withWorkers() {
        WorkerSnapshot w = new WorkerSnapshot("TRANSFORM", "transform-1", "IDLE",
                null, 0L, 0L, null, 0, null, null, NOW);
        WorkflowSnapshot snap = new WorkflowSnapshot(
                CORR_ID, "IMPORT", USER_ID, "alice", "app1", "data", "file.csv",
                NOW, "IN_PROGRESS", 0L, 0L, 0, null, 0L, 0L,
                List.of(), List.of(), List.of(w), null, null, List.of(), null, null);

        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);

        assertThat(dto.workers()).hasSize(1);
        assertThat(dto.workers().get(0).stage()).isEqualTo("TRANSFORM");
        assertThat(dto.workers().get(0).name()).isEqualTo("transform-1");
    }

    // ─── fromSnapshot — parallelism + strategy + importConfig non-null ─────────

    @Test
    @DisplayName("fromSnapshot() avec parallelism, strategy et importConfig mappés")
    void fromSnapshot_withFullConfig() {
        ParallelismSnapshot p = new ParallelismSnapshot(1, 4, 1);
        StrategySnapshot s = new StrategySnapshot("MERGE_FILE", null, "STAGED", 1);
        ImportConfigSnapshot ic = new ImportConfigSnapshot(
                1000, 200, 100, 0, 60, "shared_table", false, false, 1, 4, 1, 0);
        SinkChunkRecord sc = new SinkChunkRecord(0, "sink-1", "SUCCESS", 10L, NOW, null);

        WorkflowSnapshot snap = new WorkflowSnapshot(
                CORR_ID, "IMPORT", USER_ID, "alice", "app1", "data", "file.csv",
                NOW, "COMPLETED", 500L, 0L, 5, 100.0, 0L, 500L,
                List.of(), List.of(), List.of(), p, s, List.of(sc), ic, null);

        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);

        assertThat(dto.parallelism()).isNotNull();
        assertThat(dto.parallelism().source()).isEqualTo(1);
        assertThat(dto.parallelism().transform()).isEqualTo(4);
        assertThat(dto.strategy()).isNotNull();
        assertThat(dto.strategy().sinkStrategy()).isEqualTo("MERGE_FILE");
        assertThat(dto.importConfig()).isNotNull();
        assertThat(dto.importConfig().chunkSizeLines()).isEqualTo(1000);
        assertThat(dto.sinkChunks()).hasSize(1);
        assertThat(dto.sinkChunks().get(0).chunkIndex()).isZero();
    }

    // ─── inner records factory methods ──────────────────────────────────────────

    @Test
    @DisplayName("ChunkDTO.fromSnapshot() mappe tous les champs")
    void chunkDtoFromSnapshot() {
        ChunkSnapshot c = new ChunkSnapshot(3, "COMPLETED", 200L, 200L, "t-1",
                NOW, NOW.plus(Duration.ofSeconds(1)), null);

        DashboardWorkflowDTO.ChunkDTO dto = DashboardWorkflowDTO.ChunkDTO.fromSnapshot(c);

        assertThat(dto.chunkIndex()).isEqualTo(3);
        assertThat(dto.status()).isEqualTo("COMPLETED");
        assertThat(dto.recordsProcessed()).isEqualTo(200L);
        assertThat(dto.workerName()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("WorkerDTO.fromSnapshot() mappe tous les champs")
    void workerDtoFromSnapshot() {
        WorkerSnapshot w = new WorkerSnapshot("SINK", "sink-1", "RUNNING",
                2, 50L, 100L, 50.0, 3, Duration.ofMillis(100), Duration.ofMillis(150), NOW);

        DashboardWorkflowDTO.WorkerDTO dto = DashboardWorkflowDTO.WorkerDTO.fromSnapshot(w);

        assertThat(dto.stage()).isEqualTo("SINK");
        assertThat(dto.name()).isEqualTo("sink-1");
        assertThat(dto.status()).isEqualTo("RUNNING");
        assertThat(dto.currentChunk()).isEqualTo(2);
        assertThat(dto.currentChunkProgressPercentage()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("ParallelismDTO.fromSnapshot() mappe source/transform/sink")
    void parallelismDtoFromSnapshot() {
        ParallelismSnapshot p = new ParallelismSnapshot(2, 8, 2);
        DashboardWorkflowDTO.ParallelismDTO dto = DashboardWorkflowDTO.ParallelismDTO.fromSnapshot(p);

        assertThat(dto.source()).isEqualTo(2);
        assertThat(dto.transform()).isEqualTo(8);
        assertThat(dto.sink()).isEqualTo(2);
    }

    @Test
    @DisplayName("StrategyDTO.fromSnapshot() mappe les champs")
    void strategyDtoFromSnapshot() {
        StrategySnapshot s = new StrategySnapshot("DIRECT_COPY", "PER_CONNECTION_TEMP", "PIPELINED", 4);
        DashboardWorkflowDTO.StrategyDTO dto = DashboardWorkflowDTO.StrategyDTO.fromSnapshot(s);

        assertThat(dto.sinkStrategy()).isEqualTo("DIRECT_COPY");
        assertThat(dto.stagingStrategy()).isEqualTo("PER_CONNECTION_TEMP");
        assertThat(dto.pipelineMode()).isEqualTo("PIPELINED");
        assertThat(dto.sinkParallelism()).isEqualTo(4);
    }

    @Test
    @DisplayName("SinkChunkDTO.fromRecord() mappe tous les champs")
    void sinkChunkDtoFromRecord() {
        SinkChunkRecord r = new SinkChunkRecord(5, "sink-2", "FAILED", 50L, NOW, "pk violation");
        DashboardWorkflowDTO.SinkChunkDTO dto = DashboardWorkflowDTO.SinkChunkDTO.fromRecord(r);

        assertThat(dto.chunkIndex()).isEqualTo(5);
        assertThat(dto.workerName()).isEqualTo("sink-2");
        assertThat(dto.status()).isEqualTo("FAILED");
        assertThat(dto.errorMessage()).isEqualTo("pk violation");
    }

    @Test
    @DisplayName("ImportConfigDTO.fromSnapshot() mappe tous les champs")
    void importConfigDtoFromSnapshot() {
        ImportConfigSnapshot ic = new ImportConfigSnapshot(
                500, 100, 50, 10, 30, "oa_staging.shared", true, true, 2, 6, 2, 1);
        DashboardWorkflowDTO.ImportConfigDTO dto = DashboardWorkflowDTO.ImportConfigDTO.fromSnapshot(ic);

        assertThat(dto.chunkSizeLines()).isEqualTo(500);
        assertThat(dto.progressBatchSize()).isEqualTo(100);
        assertThat(dto.maxErrorsThreshold()).isEqualTo(50);
        assertThat(dto.enableMetrics()).isTrue();
        assertThat(dto.skipCsvReencoding()).isTrue();
        assertThat(dto.poolTransform()).isEqualTo(6);
    }

    // ─── Detail and Page inner records ──────────────────────────────────────────

    @Test
    @DisplayName("Detail record stores summary and metadata")
    void detailRecord() {
        DashboardWorkflowDTO summary = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        DashboardWorkflowDTO.Detail detail = new DashboardWorkflowDTO.Detail(
                summary, "fatal error", List.of("err1"), java.util.Map.of("key", "val"));

        assertThat(detail.summary()).isSameAs(summary);
        assertThat(detail.fatalError()).isEqualTo("fatal error");
        assertThat(detail.errors()).containsExactly("err1");
        assertThat(detail.metadata()).containsEntry("key", "val");
    }

    @Test
    @DisplayName("Page record stores items, total, limit and offset")
    void pageRecord() {
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        DashboardWorkflowDTO.Page page = new DashboardWorkflowDTO.Page(List.of(dto), 42L, 10, 0);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.offset()).isZero();
    }
}