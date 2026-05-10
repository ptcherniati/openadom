package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link WorkflowSnapshot} : factory minimal,
 * helpers with* et elapsedMillis.
 */
@Tag("domain.model")
@DisplayName("WorkflowSnapshot — snapshot immuable d'un workflow actif")
class WorkflowSnapshotTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2025-06-01T10:00:00Z");

    private WorkflowSnapshot base() {
        return WorkflowSnapshot.minimal(
                CORR_ID, "IMPORT", USER_ID, "alice",
                "myApp", "communes", "communes.csv",
                START, "IN_PROGRESS",
                0L, 0L, 0,
                null, 0L, 1000L,
                List.of(), List.of());
    }

    @Test
    @DisplayName("minimal() crée un snapshot avec les champs optionnels à null/vide")
    void minimalFactory() {
        WorkflowSnapshot s = base();

        assertEquals(CORR_ID, s.correlationId());
        assertEquals("IMPORT", s.workflowType());
        assertEquals(USER_ID, s.userId());
        assertEquals("alice", s.userLogin());
        assertEquals("myApp", s.applicationName());
        assertEquals("IN_PROGRESS", s.status());
        assertEquals(START, s.startTime());
        assertEquals(0L, s.recordsProcessed());
        assertEquals(1000L, s.recordsTotal());
        assertTrue(s.errors().isEmpty());
        assertTrue(s.chunks().isEmpty());
        assertTrue(s.workers().isEmpty());
        assertNull(s.parallelism());
        assertNull(s.strategy());
        assertTrue(s.sinkChunks().isEmpty());
        assertNull(s.importConfig());
        assertNull(s.lastHeartbeatAt());
    }

    @Test
    @DisplayName("elapsedMillis() retourne la différence en ms")
    void elapsedMillis() {
        WorkflowSnapshot s = base();
        Instant now = START.plusSeconds(10);
        assertEquals(10_000L, s.elapsedMillis(now));
    }

    @Test
    @DisplayName("withProgress() met à jour les compteurs sans toucher aux autres champs")
    void withProgress() {
        WorkflowSnapshot s = base().withProgress(500L, 3L, 2, 50.0, 102400L);

        assertEquals(500L, s.recordsProcessed());
        assertEquals(3L, s.recordsFailed());
        assertEquals(2, s.chunksProcessed());
        assertEquals(50.0, s.progressPercentage());
        assertEquals(102400L, s.bytesTotal());
        // champs inchangés
        assertEquals(CORR_ID, s.correlationId());
        assertEquals(START, s.startTime());
    }

    @Test
    @DisplayName("withRecordsTotal() met à jour le total seulement")
    void withRecordsTotal() {
        WorkflowSnapshot s = base().withRecordsTotal(5000L);
        assertEquals(5000L, s.recordsTotal());
        assertEquals(0L, s.recordsProcessed());
    }

    @Test
    @DisplayName("withChunks() remplace la liste des chunks")
    void withChunks() {
        ChunkSnapshot chunk = new ChunkSnapshot(0, "RUNNING", 0, 100,
                "t-1", START, null, null);
        WorkflowSnapshot s = base().withChunks(List.of(chunk));

        assertEquals(1, s.chunks().size());
        assertEquals(chunk, s.chunks().get(0));
    }

    @Test
    @DisplayName("withParallelism() met à jour le bloc parallelism")
    void withParallelism() {
        ParallelismSnapshot par = new ParallelismSnapshot(1, 4, 1);
        WorkflowSnapshot s = base().withParallelism(par);

        assertNotNull(s.parallelism());
        assertEquals(1, s.parallelism().source());
        assertEquals(4, s.parallelism().transform());
        assertEquals(1, s.parallelism().sink());
    }

    @Test
    @DisplayName("withSinkChunks() remplace la liste des sink chunks")
    void withSinkChunks() {
        SinkChunkRecord rec = new SinkChunkRecord(0, "sink-1", "SUCCESS", 120L, START, null);
        WorkflowSnapshot s = base().withSinkChunks(List.of(rec));

        assertEquals(1, s.sinkChunks().size());
        assertEquals(rec, s.sinkChunks().get(0));
    }

    @Test
    @DisplayName("withImportConfig() met à jour la config d'import")
    void withImportConfig() {
        ImportConfigSnapshot cfg = new ImportConfigSnapshot(
                1000, 100, 50, 500, 30, "staging_tbl",
                true, false, 1, 4, 1, 0);
        WorkflowSnapshot s = base().withImportConfig(cfg);

        assertNotNull(s.importConfig());
        assertEquals(1000, s.importConfig().chunkSizeLines());
        assertEquals(4, s.importConfig().poolTransform());
    }

    @Test
    @DisplayName("withLastHeartbeatAt() met à jour le timestamp heartbeat")
    void withLastHeartbeatAt() {
        Instant beat = START.plusSeconds(60);
        WorkflowSnapshot s = base().withLastHeartbeatAt(beat);
        assertEquals(beat, s.lastHeartbeatAt());
    }

    @Test
    @DisplayName("withStrategy() met à jour le bloc strategy")
    void withStrategy() {
        StrategySnapshot strat = new StrategySnapshot("MERGE_FILE", null, "STAGED", 1);
        WorkflowSnapshot s = base().withStrategy(strat);

        assertNotNull(s.strategy());
        assertEquals("MERGE_FILE", s.strategy().sinkStrategy());
    }

    @Test
    @DisplayName("withWorkers() remplace la liste des workers")
    void withWorkers() {
        WorkerSnapshot worker = new WorkerSnapshot(
                "TRANSFORM", "transform-1", "IDLE",
                null, 0L, 0L, null,
                5, null, null, START);
        WorkflowSnapshot s = base().withWorkers(List.of(worker));

        assertEquals(1, s.workers().size());
        assertEquals("transform-1", s.workers().get(0).name());
    }
}
