package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ChunkSnapshot}.
 */
@Tag("domain.model")
@DisplayName("ChunkSnapshot — per-chunk live state")
class ChunkSnapshotTest {

    private ChunkSnapshot running(int index, long processed, long total) {
        return new ChunkSnapshot(index, "RUNNING", processed, total,
                "transform-1", Instant.now(), null, null);
    }

    @Test
    @DisplayName("progressPercentage() retourne null quand recordsTotal <= 0")
    void progressPercentageNullWhenTotalZero() {
        ChunkSnapshot snap = running(0, 50, 0);
        assertNull(snap.progressPercentage());
    }

    @Test
    @DisplayName("progressPercentage() retourne null quand recordsTotal < 0")
    void progressPercentageNullWhenTotalNegative() {
        ChunkSnapshot snap = running(0, 50, -1);
        assertNull(snap.progressPercentage());
    }

    @Test
    @DisplayName("progressPercentage() calcule correctement 50%")
    void progressPercentageHalf() {
        ChunkSnapshot snap = running(1, 50, 100);
        assertEquals(50.0, snap.progressPercentage(), 0.001);
    }

    @Test
    @DisplayName("progressPercentage() est plafonné à 100 même si processed > total")
    void progressPercentageCappedAt100() {
        ChunkSnapshot snap = running(1, 150, 100);
        assertEquals(100.0, snap.progressPercentage(), 0.001);
    }

    @Test
    @DisplayName("withProgress() crée un nouveau snapshot avec le nouveau compteur")
    void withProgress() {
        ChunkSnapshot original = running(2, 10, 100);
        ChunkSnapshot updated = original.withProgress(75);

        assertEquals(75, updated.recordsProcessed());
        // Les autres champs doivent être préservés
        assertEquals(2, updated.chunkIndex());
        assertEquals("RUNNING", updated.status());
        assertEquals(100, updated.recordsTotal());
        assertEquals("transform-1", updated.workerName());
    }

    @Test
    @DisplayName("withEnd() crée un snapshot avec status COMPLETED et endTime")
    void withEnd() {
        Instant startTime = Instant.now().minusSeconds(5);
        Instant endTime   = Instant.now();
        ChunkSnapshot original = new ChunkSnapshot(3, "RUNNING", 50, 100,
                "transform-2", startTime, null, null);

        ChunkSnapshot ended = original.withEnd("COMPLETED", 100, endTime, null);

        assertEquals("COMPLETED", ended.status());
        assertEquals(100, ended.recordsProcessed());
        assertEquals(endTime, ended.endTime());
        assertNull(ended.errorMessage());
        assertEquals(3, ended.chunkIndex());
        assertEquals(startTime, ended.startTime());
    }

    @Test
    @DisplayName("withEnd() avec status FAILED conserve le message d'erreur")
    void withEndFailed() {
        Instant now = Instant.now();
        ChunkSnapshot original = running(4, 10, 100);
        ChunkSnapshot failed = original.withEnd("FAILED", 10, now, "DB error");

        assertEquals("FAILED", failed.status());
        assertEquals("DB error", failed.errorMessage());
    }

    @Test
    @DisplayName("record equality : deux ChunkSnapshots identiques sont égaux")
    void recordEquality() {
        Instant t = Instant.parse("2025-01-01T00:00:00Z");
        ChunkSnapshot a = new ChunkSnapshot(1, "RUNNING", 50, 100, "w-1", t, null, null);
        ChunkSnapshot b = new ChunkSnapshot(1, "RUNNING", 50, 100, "w-1", t, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
