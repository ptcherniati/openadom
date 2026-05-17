package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.history.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link DashboardWorkflowDTO} et ses sous-records.
 *
 * <p>Vérifie la logique de mapping via les factory {@code fromSnapshot()} sans
 * Spring context ni base de données.
 */
@Tag("domain.model")
@DisplayName("DashboardWorkflowDTO — records de présentation dashboard")
class DashboardWorkflowDtoTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW  = Instant.now();

    // ─── helpers builders ────────────────────────────────────────────────────

    private WorkflowSnapshot minimalSnapshot() {
        return WorkflowSnapshot.minimal(
                CORR_ID, "IMPORT", USER_ID, "alice",
                "app1", "data", "file.csv", NOW,
                "IN_PROGRESS", 0L, 0L, 0, null, 0L, 0L,
                List.of(), null);
    }

    private ChunkSnapshot chunk(int idx, String status) {
        return new ChunkSnapshot(idx, status, 100L, 200L, "t-1", NOW, null, null);
    }

    private WorkerSnapshot worker(String stage, String name) {
        return new WorkerSnapshot(stage, name, "IDLE", null,
                0L, 0L, null, 0, null, null, NOW);
    }

    private ParallelismSnapshot parallelism() {
        return new ParallelismSnapshot(1, 4, 2);
    }

    private StrategySnapshot strategy() {
        return new StrategySnapshot("MERGE_FILE", null, "STAGED", 2);
    }

    private SinkChunkRecord sinkChunk() {
        return new SinkChunkRecord(0, "sink-1", "COMPLETED", 150L, NOW, null);
    }

    private ImportConfigSnapshot importConfig() {
        return new ImportConfigSnapshot(1000, 100, 100, 0, 60, "stg_shared",
                true, false, 1, 4, 2, 0);
    }

    // ─── fromSnapshot() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("fromSnapshot() : champs de base correctement mappés")
    void fromSnapshotBaseFields() {
        WorkflowSnapshot snap = minimalSnapshot();
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);

        assertThat(dto.correlationId()).isEqualTo(CORR_ID);
        assertThat(dto.workflowType()).isEqualTo("IMPORT");
        assertThat(dto.userId()).isEqualTo(USER_ID);
        assertThat(dto.userLogin()).isEqualTo("alice");
        assertThat(dto.applicationName()).isEqualTo("app1");
        assertThat(dto.dataType()).isEqualTo("data");
        assertThat(dto.resourceName()).isEqualTo("file.csv");
        assertThat(dto.startTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("fromSnapshot() : chunks null → liste vide")
    void fromSnapshotNullChunks() {
        WorkflowSnapshot snap = minimalSnapshot();
        assertThat(snap.chunks()).isNull();
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.chunks()).isEmpty();
    }

    @Test
    @DisplayName("fromSnapshot() : chunks non-null → mappés via ChunkDTO.fromSnapshot")
    void fromSnapshotWithChunks() {
        WorkflowSnapshot snap = minimalSnapshot()
                .withChunks(List.of(chunk(0, "RUNNING"), chunk(1, "COMPLETED")));
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.chunks()).hasSize(2);
        assertThat(dto.chunks().get(0).chunkIndex()).isZero();
        assertThat(dto.chunks().get(1).status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("fromSnapshot() : workers null → liste vide")
    void fromSnapshotNullWorkers() {
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        assertThat(dto.workers()).isEmpty();
    }

    @Test
    @DisplayName("fromSnapshot() : workers non-null → mappés via WorkerDTO.fromSnapshot")
    void fromSnapshotWithWorkers() {
        WorkflowSnapshot snap = minimalSnapshot()
                .withWorkers(List.of(worker("TRANSFORM", "t-1")));
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.workers()).hasSize(1);
        assertThat(dto.workers().get(0).stage()).isEqualTo("TRANSFORM");
        assertThat(dto.workers().get(0).name()).isEqualTo("t-1");
    }

    @Test
    @DisplayName("fromSnapshot() : parallelism null → null dans le DTO")
    void fromSnapshotNullParallelism() {
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        assertThat(dto.parallelism()).isNull();
    }

    @Test
    @DisplayName("fromSnapshot() : parallelism non-null → mappé via ParallelismDTO.fromSnapshot")
    void fromSnapshotWithParallelism() {
        WorkflowSnapshot snap = minimalSnapshot().withParallelism(parallelism());
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.parallelism()).isNotNull();
        assertThat(dto.parallelism().source()).isEqualTo(1);
        assertThat(dto.parallelism().transform()).isEqualTo(4);
        assertThat(dto.parallelism().sink()).isEqualTo(2);
    }

    @Test
    @DisplayName("fromSnapshot() : strategy non-null → mappé via StrategyDTO.fromSnapshot")
    void fromSnapshotWithStrategy() {
        WorkflowSnapshot snap = minimalSnapshot().withStrategy(strategy());
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.strategy()).isNotNull();
        assertThat(dto.strategy().sinkStrategy()).isEqualTo("MERGE_FILE");
        assertThat(dto.strategy().pipelineMode()).isEqualTo("STAGED");
    }

    @Test
    @DisplayName("fromSnapshot() : sinkChunks non-null → mappés via SinkChunkDTO.fromRecord")
    void fromSnapshotWithSinkChunks() {
        WorkflowSnapshot snap = minimalSnapshot().withSinkChunks(List.of(sinkChunk()));
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.sinkChunks()).hasSize(1);
        assertThat(dto.sinkChunks().get(0).chunkIndex()).isZero();
        assertThat(dto.sinkChunks().get(0).workerName()).isEqualTo("sink-1");
    }

    @Test
    @DisplayName("fromSnapshot() : importConfig non-null → mappé via ImportConfigDTO.fromSnapshot")
    void fromSnapshotWithImportConfig() {
        WorkflowSnapshot snap = minimalSnapshot().withImportConfig(importConfig());
        DashboardWorkflowDTO dto = DashboardWorkflowDTO.fromSnapshot(snap);
        assertThat(dto.importConfig()).isNotNull();
        assertThat(dto.importConfig().chunkSizeLines()).isEqualTo(1000);
        assertThat(dto.importConfig().poolTransform()).isEqualTo(4);
    }

    // ─── ChunkDTO ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ChunkDTO.fromSnapshot() : progressPercentage calculée")
    void chunkDtoProgressPercentage() {
        ChunkSnapshot cs = new ChunkSnapshot(0, "RUNNING", 50L, 200L, "t-1", NOW, null, null);
        DashboardWorkflowDTO.ChunkDTO dto = DashboardWorkflowDTO.ChunkDTO.fromSnapshot(cs);
        assertThat(dto.progressPercentage()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("ChunkDTO.fromSnapshot() : errorMessage mappé")
    void chunkDtoErrorMessage() {
        ChunkSnapshot cs = new ChunkSnapshot(2, "FAILED", 0L, 0L, "t-2", NOW, NOW, "parse error");
        DashboardWorkflowDTO.ChunkDTO dto = DashboardWorkflowDTO.ChunkDTO.fromSnapshot(cs);
        assertThat(dto.errorMessage()).isEqualTo("parse error");
        assertThat(dto.status()).isEqualTo("FAILED");
    }

    // ─── WorkerDTO ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("WorkerDTO.fromSnapshot() : durations converties en ms")
    void workerDtoDurations() {
        WorkerSnapshot ws = new WorkerSnapshot(
                "TRANSFORM", "t-1", "IDLE", null, 0L, 0L, null, 3,
                Duration.ofMillis(500L), Duration.ofMillis(300L), NOW);
        DashboardWorkflowDTO.WorkerDTO dto = DashboardWorkflowDTO.WorkerDTO.fromSnapshot(ws);
        assertThat(dto.lastChunkDurationMs()).isEqualTo(500L);
        assertThat(dto.avgChunkDurationMs()).isEqualTo(300L);
    }

    @Test
    @DisplayName("WorkerDTO.fromSnapshot() : durations null → null")
    void workerDtoNullDurations() {
        WorkerSnapshot ws = new WorkerSnapshot(
                "SOURCE", "s-1", "RUNNING", 0, 100L, 500L, 20.0, 1,
                null, null, NOW);
        DashboardWorkflowDTO.WorkerDTO dto = DashboardWorkflowDTO.WorkerDTO.fromSnapshot(ws);
        assertThat(dto.lastChunkDurationMs()).isNull();
        assertThat(dto.avgChunkDurationMs()).isNull();
    }

    // ─── sous-records pure (records) ─────────────────────────────────────────

    @Test
    @DisplayName("ParallelismDTO : record equality")
    void parallelismDtoEquality() {
        DashboardWorkflowDTO.ParallelismDTO a = new DashboardWorkflowDTO.ParallelismDTO(1, 4, 2);
        DashboardWorkflowDTO.ParallelismDTO b = new DashboardWorkflowDTO.ParallelismDTO(1, 4, 2);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("StrategyDTO : sinkParallelism mappé")
    void strategyDtoSinkParallelism() {
        StrategySnapshot ss = new StrategySnapshot("DIRECT_COPY", "PER_CONNECTION_TEMP", "PIPELINED", 3);
        DashboardWorkflowDTO.StrategyDTO dto = DashboardWorkflowDTO.StrategyDTO.fromSnapshot(ss);
        assertThat(dto.sinkParallelism()).isEqualTo(3);
        assertThat(dto.stagingStrategy()).isEqualTo("PER_CONNECTION_TEMP");
    }

    @Test
    @DisplayName("SinkChunkDTO : record correctement construit")
    void sinkChunkDtoRecord() {
        SinkChunkRecord r = new SinkChunkRecord(5, "sink-2", "FAILED", 200L, NOW, "write error");
        DashboardWorkflowDTO.SinkChunkDTO dto = DashboardWorkflowDTO.SinkChunkDTO.fromRecord(r);
        assertThat(dto.chunkIndex()).isEqualTo(5);
        assertThat(dto.status()).isEqualTo("FAILED");
        assertThat(dto.errorMessage()).isEqualTo("write error");
        assertThat(dto.durationMs()).isEqualTo(200L);
    }

    @Test
    @DisplayName("ImportConfigDTO : tous les champs mappés")
    void importConfigDtoFields() {
        ImportConfigSnapshot ics = new ImportConfigSnapshot(500, 50, 200, 100, 120, "stg",
                false, true, 2, 8, 4, 1);
        DashboardWorkflowDTO.ImportConfigDTO dto = DashboardWorkflowDTO.ImportConfigDTO.fromSnapshot(ics);
        assertThat(dto.chunkSizeLines()).isEqualTo(500);
        assertThat(dto.poolTransform()).isEqualTo(8);
        assertThat(dto.skipCsvReencoding()).isTrue();
        assertThat(dto.enableMetrics()).isFalse();
        assertThat(dto.poolOrdering()).isEqualTo(1);
    }

    // ─── Detail + Page records ────────────────────────────────────────────────

    @Test
    @DisplayName("Detail : tous les champs accessibles")
    void detailRecord() {
        DashboardWorkflowDTO summary = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        DashboardWorkflowDTO.Detail detail = new DashboardWorkflowDTO.Detail(
                summary, "fatal", List.of("err1"), Map.of("k", "v"));
        assertThat(detail.summary()).isSameAs(summary);
        assertThat(detail.fatalError()).isEqualTo("fatal");
        assertThat(detail.errors()).containsExactly("err1");
        assertThat(detail.metadata()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("Page : tous les champs accessibles")
    void pageRecord() {
        DashboardWorkflowDTO item = DashboardWorkflowDTO.fromSnapshot(minimalSnapshot());
        DashboardWorkflowDTO.Page page = new DashboardWorkflowDTO.Page(List.of(item), 42L, 10, 0);
        assertThat(page.total()).isEqualTo(42L);
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.offset()).isZero();
        assertThat(page.items()).hasSize(1);
    }
}
