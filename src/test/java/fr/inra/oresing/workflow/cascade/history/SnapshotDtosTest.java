package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour les DTOs snapshot simples :
 * {@link ParallelismSnapshot}, {@link StrategySnapshot},
 * {@link SinkChunkRecord}, {@link WorkerSnapshot}, {@link ImportConfigSnapshot}.
 */
@Tag("domain.model")
@DisplayName("Snapshot DTOs — records immuables du dashboard oa-live")
class SnapshotDtosTest {

    // ─── ParallelismSnapshot ─────────────────────────────────────────────────

    @Test
    @DisplayName("ParallelismSnapshot.empty() retourne (0,0,0)")
    void parallelismEmpty() {
        ParallelismSnapshot p = ParallelismSnapshot.empty();
        assertEquals(0, p.source());
        assertEquals(0, p.transform());
        assertEquals(0, p.sink());
    }

    @Test
    @DisplayName("ParallelismSnapshot conserve les valeurs fournies")
    void parallelismValues() {
        ParallelismSnapshot p = new ParallelismSnapshot(1, 4, 2);
        assertEquals(1, p.source());
        assertEquals(4, p.transform());
        assertEquals(2, p.sink());
    }

    @Test
    @DisplayName("ParallelismSnapshot record equality")
    void parallelismEquality() {
        assertEquals(new ParallelismSnapshot(1, 4, 2), new ParallelismSnapshot(1, 4, 2));
        assertNotEquals(new ParallelismSnapshot(1, 4, 2), new ParallelismSnapshot(1, 4, 1));
    }

    // ─── StrategySnapshot ────────────────────────────────────────────────────

    @Test
    @DisplayName("StrategySnapshot conserve les quatre champs")
    void strategySnapshotFields() {
        StrategySnapshot s = new StrategySnapshot("MERGE_FILE", null, "STAGED", 1);
        assertEquals("MERGE_FILE", s.sinkStrategy());
        assertNull(s.stagingStrategy());
        assertEquals("STAGED", s.pipelineMode());
        assertEquals(1, s.sinkParallelism());
    }

    @Test
    @DisplayName("StrategySnapshot avec DIRECT_COPY et staging")
    void strategyDirectCopy() {
        StrategySnapshot s = new StrategySnapshot("DIRECT_COPY", "PER_CONNECTION_TEMP", "PIPELINED", 1);
        assertEquals("DIRECT_COPY", s.sinkStrategy());
        assertEquals("PER_CONNECTION_TEMP", s.stagingStrategy());
        assertEquals("PIPELINED", s.pipelineMode());
    }

    // ─── SinkChunkRecord ─────────────────────────────────────────────────────

    @Test
    @DisplayName("SinkChunkRecord conserve tous les champs")
    void sinkChunkRecord() {
        Instant now = Instant.now();
        SinkChunkRecord r = new SinkChunkRecord(5, "sink-1", "SUCCESS", 250L, now, null);
        assertEquals(5, r.chunkIndex());
        assertEquals("sink-1", r.workerName());
        assertEquals("SUCCESS", r.status());
        assertEquals(250L, r.durationMs());
        assertEquals(now, r.at());
        assertNull(r.errorMessage());
    }

    @Test
    @DisplayName("SinkChunkRecord avec erreur conserve le message")
    void sinkChunkRecordFailed() {
        Instant now = Instant.now();
        SinkChunkRecord r = new SinkChunkRecord(7, "sink-2", "FAILED", 0L, now, "timeout");
        assertEquals("FAILED", r.status());
        assertEquals("timeout", r.errorMessage());
    }

    // ─── WorkerSnapshot ──────────────────────────────────────────────────────

    @Test
    @DisplayName("WorkerSnapshot conserve tous les champs")
    void workerSnapshotFields() {
        Instant last = Instant.now();
        WorkerSnapshot w = new WorkerSnapshot(
                "TRANSFORM", "transform-2", "RUNNING",
                3, 50L, 100L, 50.0,
                2, Duration.ofMillis(200), Duration.ofMillis(180), last);

        assertEquals("TRANSFORM", w.stage());
        assertEquals("transform-2", w.name());
        assertEquals("RUNNING", w.status());
        assertEquals(3, w.currentChunk());
        assertEquals(50L, w.currentChunkRecordsProcessed());
        assertEquals(100L, w.currentChunkRecordsTotal());
        assertEquals(50.0, w.currentChunkProgressPercentage());
        assertEquals(2, w.chunkCount());
        assertEquals(Duration.ofMillis(200), w.lastChunkDuration());
        assertEquals(Duration.ofMillis(180), w.avgChunkDuration());
        assertEquals(last, w.lastActivity());
    }

    @Test
    @DisplayName("WorkerSnapshot IDLE : champs optionnels à null/0")
    void workerSnapshotIdle() {
        Instant last = Instant.now();
        WorkerSnapshot w = new WorkerSnapshot(
                "SINK", "sink-1", "IDLE",
                null, 0L, 0L, null,
                0, null, null, last);

        assertNull(w.currentChunk());
        assertNull(w.currentChunkProgressPercentage());
        assertNull(w.lastChunkDuration());
        assertNull(w.avgChunkDuration());
    }

    // ─── ImportConfigSnapshot ────────────────────────────────────────────────

    @Test
    @DisplayName("ImportConfigSnapshot conserve tous les paramètres")
    void importConfigSnapshot() {
        ImportConfigSnapshot c = new ImportConfigSnapshot(
                1000, 100, 50, 500,
                30, "staging_shared",
                true, false,
                1, 4, 1, 0);

        assertEquals(1000, c.chunkSizeLines());
        assertEquals(100, c.progressBatchSize());
        assertEquals(50, c.maxErrorsThreshold());
        assertEquals(500, c.collectorChunkSize());
        assertEquals(30, c.stagingSharedOrphanTtl());
        assertEquals("staging_shared", c.stagingSharedTableName());
        assertTrue(c.enableMetrics());
        assertFalse(c.skipCsvReencoding());
        assertEquals(1, c.poolSource());
        assertEquals(4, c.poolTransform());
        assertEquals(1, c.poolSink());
        assertEquals(0, c.poolOrdering());
    }

    @Test
    @DisplayName("ImportConfigSnapshot record equality")
    void importConfigEquality() {
        ImportConfigSnapshot a = new ImportConfigSnapshot(
                1000, 100, 50, 500, 30, "t", true, false, 1, 4, 1, 0);
        ImportConfigSnapshot b = new ImportConfigSnapshot(
                1000, 100, 50, 500, 30, "t", true, false, 1, 4, 1, 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
