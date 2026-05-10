package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PoolReloader} :
 * méthode statique {@code parseStage()} et l'enum {@link PoolReloader.Stage}.
 */
@Tag("domain.model")
@DisplayName("PoolReloader — interface et helpers statiques")
class PoolReloaderTest {

    // ─── parseStage ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseStage() reconnaît SOURCE en minuscule")
    void parseSourceLowercase() {
        assertEquals(PoolReloader.Stage.SOURCE, PoolReloader.parseStage("source"));
    }

    @Test
    @DisplayName("parseStage() reconnaît TRANSFORM en majuscule")
    void parseTransformUppercase() {
        assertEquals(PoolReloader.Stage.TRANSFORM, PoolReloader.parseStage("TRANSFORM"));
    }

    @Test
    @DisplayName("parseStage() reconnaît SINK en casse mixte")
    void parseSinkMixedCase() {
        assertEquals(PoolReloader.Stage.SINK, PoolReloader.parseStage("Sink"));
    }

    @Test
    @DisplayName("parseStage() reconnaît ORDERING")
    void parseOrdering() {
        assertEquals(PoolReloader.Stage.ORDERING, PoolReloader.parseStage("ordering"));
    }

    @Test
    @DisplayName("parseStage() lève IllegalArgumentException pour valeur inconnue")
    void parseUnknownStage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PoolReloader.parseStage("UNKNOWN"));
        assertTrue(ex.getMessage().contains("UNKNOWN") || ex.getMessage().contains("Unknown"));
    }

    @Test
    @DisplayName("parseStage() lève IllegalArgumentException pour null")
    void parseNullStage() {
        assertThrows(IllegalArgumentException.class, () -> PoolReloader.parseStage(null));
    }

    // ─── Stage enum ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Stage.values() contient exactement SOURCE, TRANSFORM, SINK, ORDERING")
    void stageValues() {
        PoolReloader.Stage[] stages = PoolReloader.Stage.values();
        assertEquals(4, stages.length);
        assertArrayEquals(new PoolReloader.Stage[]{
                PoolReloader.Stage.SOURCE,
                PoolReloader.Stage.TRANSFORM,
                PoolReloader.Stage.SINK,
                PoolReloader.Stage.ORDERING
        }, stages);
    }

    @Test
    @DisplayName("Stage.valueOf() fonctionne pour tous les stages")
    void stageValueOf() {
        assertEquals(PoolReloader.Stage.SOURCE,    PoolReloader.Stage.valueOf("SOURCE"));
        assertEquals(PoolReloader.Stage.TRANSFORM, PoolReloader.Stage.valueOf("TRANSFORM"));
        assertEquals(PoolReloader.Stage.SINK,      PoolReloader.Stage.valueOf("SINK"));
        assertEquals(PoolReloader.Stage.ORDERING,  PoolReloader.Stage.valueOf("ORDERING"));
    }

    // ─── PoolSnapshot record ─────────────────────────────────────────────────

    @Test
    @DisplayName("PoolSnapshot conserve tous les champs")
    void poolSnapshotFields() {
        PoolReloader.PoolSnapshot snap = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.TRANSFORM, 4, 4, 2, 3, 10, 50);

        assertEquals(PoolReloader.Stage.TRANSFORM, snap.stage());
        assertEquals(4, snap.corePoolSize());
        assertEquals(4, snap.maximumPoolSize());
        assertEquals(2, snap.activeCount());
        assertEquals(3, snap.poolSize());
        assertEquals(10, snap.queueSize());
        assertEquals(50, snap.queueCapacity());
    }

    @Test
    @DisplayName("PoolSnapshot record equality")
    void poolSnapshotEquality() {
        PoolReloader.PoolSnapshot a = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.SINK, 1, 1, 0, 1, 0, 10);
        PoolReloader.PoolSnapshot b = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.SINK, 1, 1, 0, 1, 0, 10);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
