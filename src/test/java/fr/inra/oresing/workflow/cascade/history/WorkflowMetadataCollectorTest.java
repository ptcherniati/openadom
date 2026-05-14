package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("domain.model")
@DisplayName("WorkflowMetadataCollector")
class WorkflowMetadataCollectorTest {

    @Test
    @DisplayName("returns null when workflow not in registry")
    void unknownCorrelationId() {
        var registry = new WorkflowActiveRegistry();
        var collector = new WorkflowMetadataCollector(registry);
        assertNull(collector.collect(UUID.randomUUID()));
    }

    @Test
    @DisplayName("returns null on null correlation id")
    void nullCorrelationId() {
        var registry = new WorkflowActiveRegistry();
        var collector = new WorkflowMetadataCollector(registry);
        assertNull(collector.collect(null));
    }

    @Test
    @DisplayName("includes parallelism + strategy + jvm when registry has data")
    void fullSnapshot() {
        var registry = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        registry.start(new WorkflowSnapshot(
                cid, "IMPORT", UUID.randomUUID(), "alice",
                "app1", "data1", "file.csv",
                Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null, null,
                java.util.List.of(),
                null,
                null));
        registry.setParallelism(cid, new ParallelismSnapshot(4, 8, 2));
        registry.setStrategy(cid, new StrategySnapshot(
                "DIRECT_COPY", "PER_CONNECTION_TEMP", "STAGED", 4));

        var collector = new WorkflowMetadataCollector(registry);
        Map<String, Object> meta = collector.collect(cid);

        assertNotNull(meta);
        assertTrue(meta.containsKey("parallelism"));
        assertTrue(meta.containsKey("strategy"));
        assertTrue(meta.containsKey("jvm"));

        @SuppressWarnings("unchecked")
        Map<String, Object> par = (Map<String, Object>) meta.get("parallelism");
        assertEquals(4, par.get("source"));
        assertEquals(8, par.get("transform"));
        assertEquals(2, par.get("sink"));

        @SuppressWarnings("unchecked")
        Map<String, Object> strat = (Map<String, Object>) meta.get("strategy");
        assertEquals("DIRECT_COPY",         strat.get("sinkStrategy"));
        assertEquals("PER_CONNECTION_TEMP", strat.get("stagingStrategy"));
        assertEquals("STAGED",              strat.get("pipelineMode"));
        assertEquals(4,                     strat.get("sinkParallelism"));

        @SuppressWarnings("unchecked")
        Map<String, Object> jvm = (Map<String, Object>) meta.get("jvm");
        assertTrue((int) jvm.get("availableProcessors") > 0);
        assertTrue((long) jvm.get("maxHeapMB") > 0);
    }

    @Test
    @DisplayName("includes binaryFileId when set via setBinaryFileId")
    void binaryFileIdPresentInMetadata() {
        var registry = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        UUID binaryFileId = UUID.randomUUID();
        registry.start(new WorkflowSnapshot(
                cid, "IMPORT", UUID.randomUUID(), "bob",
                "app2", "ref", "ref.csv",
                java.time.Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, java.util.List.of(), null, null));
        registry.setBinaryFileId(cid, binaryFileId);

        var collector = new WorkflowMetadataCollector(registry);
        Map<String, Object> meta = collector.collect(cid);

        assertNotNull(meta);
        assertTrue(meta.containsKey("binaryFileId"));
        assertEquals(binaryFileId.toString(), meta.get("binaryFileId"));
    }

    @Test
    @DisplayName("includes importConfig when setImportConfig was called")
    void importConfigPresentInMetadata() {
        var registry = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        registry.start(new WorkflowSnapshot(
                cid, "IMPORT", UUID.randomUUID(), "carol",
                "app3", "data3", "data.csv",
                java.time.Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, java.util.List.of(), null, null));
        registry.setImportConfig(cid, new ImportConfigSnapshot(
                1000, 100, 50, 200, 30, "staging_shared",
                true, false, 2, 4, 2, 0));

        var collector = new WorkflowMetadataCollector(registry);
        Map<String, Object> meta = collector.collect(cid);

        assertNotNull(meta);
        assertTrue(meta.containsKey("importConfig"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ic = (Map<String, Object>) meta.get("importConfig");
        assertEquals(1000, ic.get("chunkSizeLines"));
        assertEquals("staging_shared", ic.get("stagingSharedTableName"));
        assertEquals(true, ic.get("enableMetrics"));
    }

    @Test
    @DisplayName("returns null when registry throws RuntimeException")
    void registryExceptionReturnsNull() {
        // Créer un registry qui lève RuntimeException sur find()
        var registry = new WorkflowActiveRegistry() {
            @Override
            public java.util.Optional<WorkflowSnapshot> find(UUID id) {
                throw new RuntimeException("simulated failure");
            }
        };
        var collector = new WorkflowMetadataCollector(registry);
        assertNull(collector.collect(UUID.randomUUID()));
    }

    @Test
    @DisplayName("parallelism absent de metadata quand snapshot.parallelism() est null")
    void parallelismNullNotIncluded() {
        var registry = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        registry.start(new WorkflowSnapshot(
                cid, "IMPORT", UUID.randomUUID(), "dave",
                "app4", "data4", "d.csv",
                java.time.Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, java.util.List.of(), null, null));
        // pas de setParallelism → snapshot.parallelism() == null

        var collector = new WorkflowMetadataCollector(registry);
        Map<String, Object> meta = collector.collect(cid);

        assertNotNull(meta);
        assertFalse(meta.containsKey("parallelism"));
    }
}
