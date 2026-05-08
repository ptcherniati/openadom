package fr.inra.oresing.workflow.cascade.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link PipelineRegistry}.
 *
 * <p>Teste la logique interne (state management) sans Spring context.
 * {@code wire()} / {@code unwire()} (qui utilisent le WorkflowEventBus
 * singleton) sont évités ; on appelle directement les callbacks
 * du listener.
 */
@Tag("domain.model")
@DisplayName("PipelineRegistry — gestion d'état pipeline en mémoire")
class PipelineRegistryTest {

    private PipelineRegistry registry;
    private UUID             corrId;
    private String           corrIdStr;

    @BeforeEach
    void setUp() {
        registry   = new PipelineRegistry();
        corrId     = UUID.randomUUID();
        corrIdStr  = corrId.toString();
    }

    // ─── helpers événements ──────────────────────────────────────────────────

    private WorkflowEvents.WorkflowStartEvent startEvent() {
        // (correlationId, userId, sourceName, sinkName, startTime)
        return new WorkflowEvents.WorkflowStartEvent(
                corrIdStr, null, "SOURCE", "SINK", Instant.now());
    }

    private WorkflowEvents.WorkflowEndEvent endEvent() {
        // (correlationId, userId, status, recordsProcessed, recordsFailed,
        //  chunksProcessed, startTime, endTime, duration, errors, fatalError)
        return new WorkflowEvents.WorkflowEndEvent(
                corrIdStr, null, ProcessingStatus.SUCCESS,
                0L, 0L, 0,
                Instant.now(), Instant.now(), Duration.ZERO,
                List.of(), null);
    }

    // ─── cycle de vie basique ────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot() retourne empty avant WorkflowStart")
    void emptyBeforeStart() {
        assertFalse(registry.snapshot(corrId).isPresent());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("onWorkflowStart() enregistre l'entrée")
    void startRegistersEntry() {
        registry.onWorkflowStart(startEvent());

        assertEquals(1, registry.size());
        Optional<PipelineSnapshot> snap = registry.snapshot(corrId);
        assertTrue(snap.isPresent());
        assertEquals(corrId, snap.get().correlationId());
    }

    @Test
    @DisplayName("onWorkflowEnd() supprime l'entrée")
    void endRemovesEntry() {
        registry.onWorkflowStart(startEvent());
        registry.onWorkflowEnd(endEvent());

        assertEquals(0, registry.size());
        assertFalse(registry.snapshot(corrId).isPresent());
    }

    @Test
    @DisplayName("deux workflows simultanés sont trackés indépendamment")
    void twoWorkflowsIndependent() {
        UUID other = UUID.randomUUID();
        registry.onWorkflowStart(startEvent());
        registry.onWorkflowStart(
                new WorkflowEvents.WorkflowStartEvent(
                        other.toString(), null, "SOURCE", "SINK", Instant.now()));

        assertEquals(2, registry.size());
        assertTrue(registry.snapshot(corrId).isPresent());
        assertTrue(registry.snapshot(other).isPresent());
    }

    // ─── événements source/transform/sink ───────────────────────────────────

    @Test
    @DisplayName("onSourceTotalChunksKnown() met à jour totalChunks")
    void sourceTotalChunksKnown() {
        registry.onWorkflowStart(startEvent());
        // (correlationId, totalChunks, time)
        registry.onSourceTotalChunksKnown(
                new WorkflowEvents.SourceTotalChunksKnownEvent(corrIdStr, 10, Instant.now()));

        PipelineSnapshot snap = registry.snapshot(corrId).orElseThrow();
        assertEquals(10, snap.totalChunks());
    }

    @Test
    @DisplayName("onSourceChunkEmitted() incrémente chunksDoneTotal du worker source")
    void sourceChunkEmitted() {
        registry.onWorkflowStart(startEvent());
        // SourceFetchStartEvent(correlationId, slotIndex, workerName, time)
        registry.onSourceFetchStart(
                new WorkflowEvents.SourceFetchStartEvent(corrIdStr, 0, "source-1", Instant.now()));
        // SourceChunkEmittedEvent(correlationId, chunkIndex, recordsEmitted,
        //   workerName, sourceQueueDepth, status, errorMessage, time)
        registry.onSourceChunkEmitted(
                new WorkflowEvents.SourceChunkEmittedEvent(
                        corrIdStr, 0, 100L, "source-1", 0,
                        ProcessingStatus.SUCCESS, null, Instant.now()));

        PipelineSnapshot snap = registry.snapshot(corrId).orElseThrow();
        assertNotNull(snap.source());
        assertEquals(1, snap.source().workers().stream()
                .filter(w -> "source-1".equals(w.name()))
                .findFirst().orElseThrow().chunksDoneTotal());
    }

    @Test
    @DisplayName("onChunkStart() marque le worker transform RUNNING")
    void chunkStart() {
        registry.onWorkflowStart(startEvent());
        // ChunkStartEvent(correlationId, chunkIndex, recordsExpected, workerName, startTime)
        registry.onChunkStart(
                new WorkflowEvents.ChunkStartEvent(corrIdStr, 0, 200L, "transform-1", Instant.now()));

        PipelineSnapshot snap = registry.snapshot(corrId).orElseThrow();
        assertEquals("RUNNING", snap.transform().workers().stream()
                .filter(w -> "transform-1".equals(w.name()))
                .findFirst().orElseThrow().status());
    }

    @Test
    @DisplayName("onChunkEnd() remet le worker IDLE et incrémente chunksDoneTotal")
    void chunkEnd() {
        registry.onWorkflowStart(startEvent());
        Instant start = Instant.now();
        registry.onChunkStart(
                new WorkflowEvents.ChunkStartEvent(corrIdStr, 0, 200L, "transform-1", start));
        // ChunkEndEvent(correlationId, chunkIndex, status, recordsProcessed, recordsFailed,
        //   workerName, startTime, endTime, duration, errorMessage)
        registry.onChunkEnd(
                new WorkflowEvents.ChunkEndEvent(
                        corrIdStr, 0, ProcessingStatus.SUCCESS,
                        200L, 0L, "transform-1",
                        start, start.plusMillis(500), Duration.ofMillis(500), null));

        PipelineSnapshot snap = registry.snapshot(corrId).orElseThrow();
        var worker = snap.transform().workers().stream()
                .filter(w -> "transform-1".equals(w.name()))
                .findFirst().orElseThrow();
        assertEquals("IDLE", worker.status());
        assertEquals(1, worker.chunksDoneTotal());
    }

    // ─── UUID invalide ───────────────────────────────────────────────────────

    @Test
    @DisplayName("événements avec correlationId invalide sont ignorés silencieusement")
    void invalidCorrelationIdIgnored() {
        assertDoesNotThrow(() -> registry.onWorkflowStart(
                new WorkflowEvents.WorkflowStartEvent(
                        "not-a-uuid", null, "SOURCE", "SINK", Instant.now())));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("événements avec correlationId null sont ignorés silencieusement")
    void nullCorrelationIdIgnored() {
        assertDoesNotThrow(() -> registry.onWorkflowStart(
                new WorkflowEvents.WorkflowStartEvent(
                        null, null, "SOURCE", "SINK", Instant.now())));
        assertEquals(0, registry.size());
    }

    // ─── RollingThroughput (internal) ────────────────────────────────────────

    @Test
    @DisplayName("RollingThroughput : linesPerSec = 0 si aucun sample")
    void rollingThroughputEmpty() {
        PipelineRegistry.RollingThroughput tput = new PipelineRegistry.RollingThroughput(5_000L);
        assertEquals(0d, tput.linesPerSec(Instant.now()));
    }

    @Test
    @DisplayName("RollingThroughput : samples à records <= 0 ignorés")
    void rollingThroughputIgnoresNonPositive() {
        PipelineRegistry.RollingThroughput tput = new PipelineRegistry.RollingThroughput(5_000L);
        tput.add(0, Instant.now());
        tput.add(-5, Instant.now());
        assertEquals(0d, tput.linesPerSec(Instant.now()));
    }

    @Test
    @DisplayName("RollingThroughput : sample at=null ignoré")
    void rollingThroughputIgnoresNullAt() {
        PipelineRegistry.RollingThroughput tput = new PipelineRegistry.RollingThroughput(5_000L);
        tput.add(100, null);
        assertEquals(0d, tput.linesPerSec(Instant.now()));
    }

    @Test
    @DisplayName("RollingThroughput : linesPerSec > 0 après un sample valide")
    void rollingThroughputPositiveAfterSample() {
        PipelineRegistry.RollingThroughput tput = new PipelineRegistry.RollingThroughput(5_000L);
        Instant t = Instant.now().minusSeconds(1);
        tput.add(1000, t);
        assertTrue(tput.linesPerSec(Instant.now()) > 0d);
    }

    @Test
    @DisplayName("RollingThroughput : samples hors fenêtre sont évincés")
    void rollingThroughputEvictsOldSamples() {
        PipelineRegistry.RollingThroughput tput = new PipelineRegistry.RollingThroughput(1_000L);
        Instant old = Instant.now().minusSeconds(10);
        tput.add(9999, old);
        assertEquals(0d, tput.linesPerSec(Instant.now()));
    }
}
