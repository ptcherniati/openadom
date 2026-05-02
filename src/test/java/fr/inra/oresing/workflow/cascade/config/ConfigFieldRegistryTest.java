package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigFieldRegistry")
class ConfigFieldRegistryTest {

    private ImportProperties props;
    private FakePoolReloader fakeReloader;
    private ConfigFieldRegistry registry;

    @BeforeEach
    void setUp() {
        props = new ImportProperties();
        fakeReloader = new FakePoolReloader();
        // Construit deux rate limiters reels avec des params minimaux pour
        // que le registry puisse s'initialiser . Le bean OpenadomMetrics
        // et WorkflowLogWriter ne sont pas appelles pendant les tests
        // ConfigFieldRegistry , donc null est suffisant pour les setters .
        fr.inra.oresing.workflow.cascade.ImportRateLimiter importRl =
                new fr.inra.oresing.workflow.cascade.ImportRateLimiter(
                        3, null, null, null);
        fr.inra.oresing.workflow.cascade.ExtractionRateLimiter extractionRl =
                new fr.inra.oresing.workflow.cascade.ExtractionRateLimiter(
                        5, 0L, null, null, null);
        registry = new ConfigFieldRegistry(props, fakeReloader, importRl, extractionRl);
        registry.registerAll();
    }

    @Test
    @DisplayName("schema includes hot and cold fields")
    void schema() {
        var schema = registry.schema();
        assertTrue(schema.size() > 10);
        assertTrue(schema.stream().anyMatch(f -> f.name().equals("chunkSizeLines") && f.hot()));
        assertTrue(schema.stream().anyMatch(f -> f.name().equals("virtualThreads") && f.restartRequired()));
        assertTrue(schema.stream().anyMatch(f -> f.name().equals("queueSize.source") && f.restartRequired()));
        assertTrue(schema.stream().anyMatch(f -> f.name().startsWith("pool.") && f.hot()));
    }

    @Test
    @DisplayName("apply int field within range")
    void applyIntOk() {
        var m = registry.apply("chunkSizeLines", 5000);
        assertTrue(m.changed());
        assertEquals(5000, props.getChunkSizeLines());
    }

    @Test
    @DisplayName("apply int out of range throws")
    void applyIntOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.apply("chunkSizeLines", 0));
        assertEquals(1000, props.getChunkSizeLines()); // unchanged
    }

    @Test
    @DisplayName("apply enum invalid throws")
    void applyEnumInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.apply("sinkStrategy", "WHATEVER"));
    }

    @Test
    @DisplayName("apply read-only field throws UnsupportedOperationException")
    void applyReadOnly() {
        assertThrows(UnsupportedOperationException.class,
                () -> registry.apply("virtualThreads", true));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.apply("queueSize.source", 200));
    }

    @Test
    @DisplayName("apply unknown field throws NoSuchElementException")
    void applyUnknownField() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> registry.apply("nonExistent", 42));
    }

    @Test
    @DisplayName("pool.X resize delegates to PoolReloader")
    void applyPoolField() {
        registry.apply("pool.transform", 8);
        assertEquals(8, fakeReloader.lastResize.get(PoolReloader.Stage.TRANSFORM));
    }

    @Test
    @DisplayName("snapshot returns current values for every field")
    void snapshot() {
        Map<String, Object> snap = registry.snapshot();
        assertEquals(1000, snap.get("chunkSizeLines"));
        assertEquals("MERGE_FILE", snap.get("sinkStrategy"));
        assertNotNull(snap.get("pool.source"));
    }

    @Test
    @DisplayName("apply same value returns changed=false")
    void noChange() {
        var m = registry.apply("chunkSizeLines", 1000); // default
        assertFalse(m.changed());
    }

    @Test
    @DisplayName("apply rate limit import quota")
    void applyImportQuota() {
        var m = registry.apply("rateLimit.import.maxConcurrentPerUser", 25);
        assertTrue(m.changed());
        // value should be reflected via getter on next snapshot
        Object v = registry.snapshot().get("rateLimit.import.maxConcurrentPerUser");
        assertEquals(25, v);
    }

    @Test
    @DisplayName("rate limit fields are hot in schema")
    void rateLimitHot() {
        var schema = registry.schema();
        assertTrue(schema.stream().anyMatch(f ->
                f.name().equals("rateLimit.import.maxConcurrentPerUser") && f.hot()));
        assertTrue(schema.stream().anyMatch(f ->
                f.name().equals("rateLimit.extraction.maxConcurrentPerUser") && f.hot()));
        assertTrue(schema.stream().anyMatch(f ->
                f.name().equals("rateLimit.extraction.acquireTimeoutSeconds") && f.hot()));
    }

    // ---- fakes ----

    static final class FakePoolReloader implements PoolReloader {
        final Map<Stage, Integer> sizes = new EnumMap<>(Stage.class);
        final Map<Stage, Integer> lastResize = new EnumMap<>(Stage.class);

        FakePoolReloader() {
            for (Stage s : Stage.values()) sizes.put(s, 4);
        }

        @Override
        public void resize(Stage stage, int newSize) {
            if (newSize < 1) throw new IllegalArgumentException("size must be >= 1");
            sizes.put(stage, newSize);
            lastResize.put(stage, newSize);
        }

        @Override
        public PoolSnapshot snapshot(Stage stage) {
            int n = sizes.getOrDefault(stage, -1);
            return new PoolSnapshot(stage, n, n, 0, n, 0, 100);
        }
    }

    @SuppressWarnings("unused") // utilise par AtomicInteger import - clean
    AtomicInteger __unused;
}
