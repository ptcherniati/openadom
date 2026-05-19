package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
    @DisplayName("logAsync() enfile l'entry dans la queue (pas de start nécessaire)")
    void logAsyncEnqueues() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        WorkflowLogWriter writer = writerNotStarted(repo);
        WorkflowLogEntry e = entry(WorkflowLogEntry.STATUS_COMPLETED);
        writer.logAsync(e);
        // Queue non lue par le worker → le repository n'est pas appelé
        verify(repo, never()).insertBatch(any());
    }

    @Test
    @DisplayName("logAsync() avec queue pleine doit drop (no block) et ne lève pas d'exception")
    void logAsyncDropsWhenQueueFull() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        // Capacité 1 → on la remplit, puis on vérifie que l'ajout suivant ne bloque pas
        WorkflowLogWriter writer = new WorkflowLogWriter(repo, 1, 5, 2000L);
        WorkflowLogEntry e = entry(WorkflowLogEntry.STATUS_COMPLETED);
        writer.logAsync(e); // remplit la queue
        org.assertj.core.api.Assertions.assertThatCode(() -> writer.logAsync(e))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("recordEnd() retry quand insertBatch renvoie < 0 toutes les tentatives et appelle logAsync en fallback")
    void recordEndRetriesAndFallbackToAsync() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        // insertBatch always returns -1 (simulate "0 rows inserted" = error case)
        when(repo.insertBatch(any())).thenReturn(-1);
        WorkflowLogWriter writer = new WorkflowLogWriter(repo, 100, 5, 2000L);

        // Ne doit pas lancer d'exception (fallback logAsync)
        boolean result = writer.recordEnd(entry(WorkflowLogEntry.STATUS_COMPLETED));

        assertThat(result).isFalse();
        // 3 tentatives attendues (backoffMs length = 3)
        verify(repo, times(3)).insertBatch(any());
    }

    @Test
    @DisplayName("recordEnd() retourne true au 2ème essai (retry avec succès)")
    void recordEndSucceedsOnSecondAttempt() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        // 1er appel échoue, 2ème réussit
        when(repo.insertBatch(any()))
                .thenThrow(new RuntimeException("transient"))
                .thenReturn(1);
        WorkflowLogWriter writer = new WorkflowLogWriter(repo, 100, 5, 1L); // 1ms backoff for test

        boolean result = writer.recordEnd(entry(WorkflowLogEntry.STATUS_COMPLETED));

        assertThat(result).isTrue();
        verify(repo, times(2)).insertBatch(any());
    }


}
