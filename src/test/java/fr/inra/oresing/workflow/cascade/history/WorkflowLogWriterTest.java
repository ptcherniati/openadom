package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link WorkflowLogWriter} — logique fire-and-forget
 * avec queue bornée, {@code recordStart}, {@code recordEnd}, {@code logAsync}.
 *
 * <p>Pas de test du thread démon (flush async) : on vérifie uniquement les
 * chemins synchrones et le comportement queue-pleine sans démarrer le worker.
 */
@Tag("domain.model")
@DisplayName("WorkflowLogWriter — writer asynchrone workflow_log")
class WorkflowLogWriterTest {

    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW  = Instant.now();

    private WorkflowLogEntry entry(String status) {
        return new WorkflowLogEntry(
                CORR_ID, WorkflowLogEntry.TYPE_IMPORT, USER_ID, "alice",
                "app1", "data", "file.csv",
                NOW, NOW, Duration.ofSeconds(1),
                status, 100L, 0L, 1, 0L,
                List.of(), null);
    }

    /** Crée un writer NON démarré (sans @PostConstruct). */
    private WorkflowLogWriter writerNotStarted(WorkflowLogRepository repo) {
        return new WorkflowLogWriter(repo, 10, 5, 2000L);
    }

    // ─── logAsync ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logAsync(null) est un no-op")
    void logAsyncNull() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        WorkflowLogWriter writer = writerNotStarted(repo);
        writer.logAsync(null);
        verify(repo, never()).insertBatch(any());
    }

    // ─── recordStart ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordStart(null) est un no-op")
    void recordStartNull() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        WorkflowLogWriter writer = writerNotStarted(repo);
        writer.recordStart(null);
        verify(repo, never()).recordStart(any());
    }

    @Test
    @DisplayName("recordStart() appelle repository.recordStart()")
    void recordStartCallsRepo() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.recordStart(any())).thenReturn(true);
        WorkflowLogWriter writer = writerNotStarted(repo);

        writer.recordStart(entry(WorkflowLogEntry.STATUS_IN_PROGRESS));

        verify(repo, times(1)).recordStart(any());
    }

    @Test
    @DisplayName("recordStart() survit à une exception DB (best-effort)")
    void recordStartSurvivesException() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.recordStart(any())).thenThrow(new RuntimeException("DB down"));
        WorkflowLogWriter writer = writerNotStarted(repo);

        // Ne doit pas propager l'exception
        WorkflowLogEntry e = entry(WorkflowLogEntry.STATUS_IN_PROGRESS);
        // recordStart fait 4 tentatives avec des backoffs (200, 500, 1000 ms)
        // Pour accélérer le test, on simule via repo qui échoue toujours.
        // Le test vérifie que la méthode absorbe l'exception finale.
        org.assertj.core.api.Assertions.assertThatCode(() -> writer.recordStart(e))
                .doesNotThrowAnyException();
    }

    // ─── recordEnd ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordEnd(null) retourne false et ne touche pas le repo")
    void recordEndNull() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        WorkflowLogWriter writer = writerNotStarted(repo);

        boolean result = writer.recordEnd(null);

        assertThat(result).isFalse();
        verify(repo, never()).insertBatch(any());
    }

    @Test
    @DisplayName("recordEnd() retourne true quand le repo insère avec succès")
    void recordEndSuccess() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.insertBatch(any())).thenReturn(1);
        WorkflowLogWriter writer = writerNotStarted(repo);

        boolean result = writer.recordEnd(entry(WorkflowLogEntry.STATUS_COMPLETED));

        assertThat(result).isTrue();
        verify(repo, times(1)).insertBatch(any());
    }
}
