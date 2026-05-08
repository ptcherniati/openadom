package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link WorkflowLogEntry} et {@link FinalizePhaseSnapshot}.
 *
 * <p>Couvre les constructeurs compat et les méthodes de factory/builder
 * non encore exercés par les tests existants.
 */
@Tag("domain.model")
@DisplayName("WorkflowLogEntry et FinalizePhaseSnapshot — tests unitaires")
class WorkflowHistoryTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ─── WorkflowLogEntry — constructeurs compat ────────────────────────────

    @Test
    @DisplayName("constructeur compat 17-arg (sans failedStage ni finalCount)")
    void compatConstructorWithMetadata() {
        Map<String, Object> meta = Map.of("parallelism", 4);
        WorkflowLogEntry entry = new WorkflowLogEntry(
                CORR_ID, WorkflowLogEntry.TYPE_IMPORT, USER_ID, "alice",
                "myApp", "communes", "communes.csv",
                Instant.now(), Instant.now(), Duration.ofSeconds(10),
                WorkflowLogEntry.STATUS_COMPLETED,
                1000L, 0L, 5, 204800L, List.of(), null,
                meta);

        assertEquals(CORR_ID, entry.correlationId());
        assertNull(entry.failedStage());
        assertNull(entry.finalCount());
        assertEquals(meta, entry.metadata());
    }

    @Test
    @DisplayName("constructeur compat 18-arg (avec failedStage, sans finalCount)")
    void compatConstructorWithFailedStage() {
        WorkflowLogEntry entry = new WorkflowLogEntry(
                CORR_ID, WorkflowLogEntry.TYPE_IMPORT, USER_ID, "alice",
                "myApp", "communes", "communes.csv",
                Instant.now(), Instant.now(), Duration.ofSeconds(3),
                WorkflowLogEntry.STATUS_FAILED,
                0L, 10L, 1, 0L, List.of("some error"), "fatal",
                null, "SOURCE");

        assertEquals("SOURCE", entry.failedStage());
        assertNull(entry.finalCount());
    }

    @Test
    @DisplayName("startMarker() crée entry IN_PROGRESS avec bons champs")
    void startMarker() {
        Instant now = Instant.now();
        WorkflowLogEntry marker = WorkflowLogEntry.startMarker(
                CORR_ID, WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, "bob",
                "app1", "communes", "output.zip", now, 1024L);

        assertEquals(CORR_ID, marker.correlationId());
        assertEquals(WorkflowLogEntry.STATUS_IN_PROGRESS, marker.status());
        assertEquals(WorkflowLogEntry.TYPE_EXTRACT_ZIP, marker.workflowType());
        assertNull(marker.endTime());
        assertNull(marker.duration());
        assertEquals(1024L, marker.bytesTotal());
        assertTrue(marker.errors().isEmpty());
        assertNull(marker.fatalError());
    }

    @Test
    @DisplayName("constantes TYPE_* et STATUS_* ont les bonnes valeurs")
    void constants() {
        assertEquals("IMPORT", WorkflowLogEntry.TYPE_IMPORT);
        assertEquals("EXTRACT_ZIP", WorkflowLogEntry.TYPE_EXTRACT_ZIP);
        assertEquals("EXTRACT_CSV", WorkflowLogEntry.TYPE_EXTRACT_CSV);
        assertEquals("EXTRACT_ADDITIONAL_FILES", WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES);
        assertEquals("EXTRACT_CHARTE", WorkflowLogEntry.TYPE_EXTRACT_CHARTE);

        assertEquals("IN_PROGRESS", WorkflowLogEntry.STATUS_IN_PROGRESS);
        assertEquals("COMPLETED", WorkflowLogEntry.STATUS_COMPLETED);
        assertEquals("FAILED", WorkflowLogEntry.STATUS_FAILED);
        assertEquals("CANCELLED", WorkflowLogEntry.STATUS_CANCELLED);
        assertEquals("RATE_LIMITED", WorkflowLogEntry.STATUS_RATE_LIMITED);
        assertEquals("UPLOADING", WorkflowLogEntry.STATUS_UPLOADING);
        assertEquals("CHUNKING", WorkflowLogEntry.STATUS_CHUNKING);
        assertEquals("PROCESSING", WorkflowLogEntry.STATUS_PROCESSING);
        assertEquals("LOADING_DB", WorkflowLogEntry.STATUS_LOADING_DB);
    }

    // ─── FinalizePhaseSnapshot — factory/builder ─────────────────────────────

    @Test
    @DisplayName("starting() crée snapshot CASCADE_RUNNING")
    void startingSnapshot() {
        Instant now = Instant.now();
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(now);

        assertEquals(FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING, snap.phase());
        assertEquals(now, snap.cascadeStartedAt());
        assertNull(snap.cascadeFinishedAt());
        assertNull(snap.finalizeStartedAt());
        assertNull(snap.errorMessage());
    }

    @Test
    @DisplayName("withCascadeFinished() transite vers FINALIZE_RUNNING")
    void withCascadeFinished() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(5);
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(start)
                .withCascadeFinished(end);

        assertEquals(FinalizePhaseSnapshot.PHASE_FINALIZE_RUNNING, snap.phase());
        assertEquals(end, snap.cascadeFinishedAt());
        assertEquals(end, snap.finalizeStartedAt());
    }

    @Test
    @DisplayName("withFinalizeFinished() transite vers COMPLETED")
    void withFinalizeFinished() {
        Instant start = Instant.now();
        Instant cascadeEnd = start.plusSeconds(5);
        Instant finalEnd = start.plusSeconds(8);
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(start)
                .withCascadeFinished(cascadeEnd)
                .withFinalizeFinished(finalEnd);

        assertEquals(FinalizePhaseSnapshot.PHASE_COMPLETED, snap.phase());
        assertEquals(finalEnd, snap.finalizeFinishedAt());
    }

    @Test
    @DisplayName("withRollbackStarted() transite vers ROLLBACK_IN_PROGRESS")
    void withRollbackStarted() {
        Instant start = Instant.now();
        Instant rollbackAt = start.plusSeconds(3);
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(start)
                .withRollbackStarted(rollbackAt, 150L, "UPSERT failed");

        assertEquals(FinalizePhaseSnapshot.PHASE_ROLLBACK_IN_PROGRESS, snap.phase());
        assertEquals(rollbackAt, snap.rollbackStartedAt());
        assertEquals(150L, snap.rowsBeforeRollback());
        assertEquals("UPSERT failed", snap.errorMessage());
    }

    @Test
    @DisplayName("withRollbackFinished() transite vers ROLLBACK_DONE")
    void withRollbackFinished() {
        Instant start = Instant.now();
        Instant rollbackEnd = start.plusSeconds(6);
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(start)
                .withRollbackStarted(start.plusSeconds(2), 50L, "error")
                .withRollbackFinished(rollbackEnd);

        assertEquals(FinalizePhaseSnapshot.PHASE_ROLLBACK_DONE, snap.phase());
        assertEquals(rollbackEnd, snap.rollbackFinishedAt());
    }

    @Test
    @DisplayName("constantes PHASE_* ont les bonnes valeurs")
    void phaseConstants() {
        assertEquals("CASCADE_RUNNING", FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING);
        assertEquals("FINALIZE_RUNNING", FinalizePhaseSnapshot.PHASE_FINALIZE_RUNNING);
        assertEquals("COMPLETED", FinalizePhaseSnapshot.PHASE_COMPLETED);
        assertEquals("ROLLBACK_IN_PROGRESS", FinalizePhaseSnapshot.PHASE_ROLLBACK_IN_PROGRESS);
        assertEquals("ROLLBACK_DONE", FinalizePhaseSnapshot.PHASE_ROLLBACK_DONE);
    }
}
