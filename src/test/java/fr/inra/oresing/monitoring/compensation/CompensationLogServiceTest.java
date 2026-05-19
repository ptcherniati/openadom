package fr.inra.oresing.monitoring.compensation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link CompensationLogService}.
 *
 * <p>Couvre : record(), confirm(), skipAndDelete(), runHandlerAndDelete()
 * (succès / échec / handler absent), compensateNow() (row déjà disparue),
 * listPending(), listFailed(), deleteFailedOlderThan().
 */
@Tag("domain.model")
@DisplayName("CompensationLogService — API publique du journal de compensation")
class CompensationLogServiceTest {

    private static final UUID ID      = UUID.randomUUID();
    private static final UUID CORR_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private CompensationLogEntry entry(String type, String status) {
        return new CompensationLogEntry(
                ID, type, "oa_data", "binary_file", "id-99",
                CORR_ID, USER_ID, "alice",
                Map.of(),
                status,
                Instant.now(), CompensationLogEntry.DEFAULT_TTL_MINUTES,
                0, null, null);
    }

    private CompensationLogService service(CompensationLogRepository repo,
                                           CompensationHandlerRegistry registry) {
        return new CompensationLogService(repo, registry);
    }

    // ─── record() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("record() délègue à repository.recordPending() et retourne l'UUID")
    void recordDelegates() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of());
        when(repo.recordPending(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(ID);

        CompensationLogService svc = service(repo, registry);
        UUID result = svc.record("TYPE", "oa_data", "t", "id", CORR_ID, USER_ID, "alice",
                Map.of(), 60);

        assertThat(result).isEqualTo(ID);
        verify(repo).recordPending(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("record() avec TTL par défaut appelle la surcharge 8-args")
    void recordDefaultTtl() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandlerRegistry registry = new CompensationHandlerRegistry(List.of());
        when(repo.recordPending(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(ID);

        CompensationLogService svc = service(repo, registry);
        UUID result = svc.record("TYPE", "oa_data", "t", "id", CORR_ID, USER_ID, "alice", Map.of());

        assertThat(result).isEqualTo(ID);
    }

    // ─── confirm() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("confirm() retourne true quand le repo supprime la row")
    void confirmSuccess() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        when(repo.delete(ID)).thenReturn(true);
        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));

        assertThat(svc.confirm(ID)).isTrue();
        verify(repo).delete(ID);
    }

    @Test
    @DisplayName("confirm() retourne false quand la row n'existe plus (idempotent)")
    void confirmAlreadyGone() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        when(repo.delete(ID)).thenReturn(false);
        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));

        assertThat(svc.confirm(ID)).isFalse();
    }

    // ─── skipAndDelete() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("skipAndDelete() délègue à repository.delete() sans exécuter le handler")
    void skipAndDeleteDelegates() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandler handler = mock(CompensationHandler.class);
        when(handler.operationType()).thenReturn("TYPE");
        when(repo.delete(ID)).thenReturn(true);

        CompensationLogService svc = service(repo,
                new CompensationHandlerRegistry(List.of(handler)));
        assertThat(svc.skipAndDelete(ID)).isTrue();

        verify(handler, never()).compensate(any());
        verify(repo).delete(ID);
    }

    // ─── runHandlerAndDelete() ────────────────────────────────────────────────

    @Test
    @DisplayName("runHandlerAndDelete() : handler trouvé + succès → DELETE + true")
    void runHandlerSuccess() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandler handler = mock(CompensationHandler.class);
        when(handler.operationType()).thenReturn("BF");

        CompensationLogEntry e = entry("BF", "PENDING");
        CompensationLogService svc = service(repo,
                new CompensationHandlerRegistry(List.of(handler)));
        boolean ok = svc.runHandlerAndDelete(e);

        assertThat(ok).isTrue();
        verify(handler).compensate(e);
        verify(repo).delete(ID);
    }

    @Test
    @DisplayName("runHandlerAndDelete() : handler lève exception → recordFailure + false")
    void runHandlerFails() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandler handler = mock(CompensationHandler.class);
        when(handler.operationType()).thenReturn("BF");
        doThrow(new RuntimeException("cleanup failed")).when(handler).compensate(any());

        CompensationLogEntry e = entry("BF", "PENDING");
        CompensationLogService svc = service(repo,
                new CompensationHandlerRegistry(List.of(handler)));
        boolean ok = svc.runHandlerAndDelete(e);

        assertThat(ok).isFalse();
        verify(repo).recordFailure(eq(ID), anyString(), anyInt());
        verify(repo, never()).delete(ID);
    }

    @Test
    @DisplayName("runHandlerAndDelete() : pas de handler → recordFailure + false")
    void runHandlerNoHandler() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationLogEntry e = entry("UNKNOWN_TYPE", "PENDING");

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        boolean ok = svc.runHandlerAndDelete(e);

        assertThat(ok).isFalse();
        verify(repo).recordFailure(eq(ID), anyString(), anyInt());
    }

    // ─── compensateNow() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("compensateNow() : row absente → no-op, retourne true")
    void compensateNowRowGone() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        when(repo.findById(ID)).thenReturn(null);

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThat(svc.compensateNow(ID)).isTrue();
        verify(repo, never()).delete(ID);
    }

    @Test
    @DisplayName("compensateNow() : row présente + handler OK → retourne true")
    void compensateNowSuccess() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationHandler handler = mock(CompensationHandler.class);
        when(handler.operationType()).thenReturn("BF");
        CompensationLogEntry e = entry("BF", "PENDING");
        when(repo.findById(ID)).thenReturn(e);

        CompensationLogService svc = service(repo,
                new CompensationHandlerRegistry(List.of(handler)));
        assertThat(svc.compensateNow(ID)).isTrue();
        verify(handler).compensate(e);
    }

    // ─── list queries ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listPending() délègue au repo avec STATUS_PENDING")
    void listPending() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationLogEntry e = entry("BF", "PENDING");
        when(repo.findByStatus(CompensationLogEntry.STATUS_PENDING, 50)).thenReturn(List.of(e));

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThat(svc.listPending(50)).containsExactly(e);
    }

    @Test
    @DisplayName("listFailed() délègue au repo avec STATUS_FAILED")
    void listFailed() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationLogEntry e = entry("BF", "FAILED");
        when(repo.findByStatus(CompensationLogEntry.STATUS_FAILED, 10)).thenReturn(List.of(e));

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThat(svc.listFailed(10)).containsExactly(e);
    }

    @Test
    @DisplayName("deleteFailedOlderThan() délègue au repo")
    void deleteFailedOlderThan() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        when(repo.deleteFailedOlderThan(7)).thenReturn(3);

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThat(svc.deleteFailedOlderThan(7)).isEqualTo(3);
    }

    @Test
    @DisplayName("findById() délègue au repo")
    void findById() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationLogEntry e = entry("BF", "PENDING");
        when(repo.findById(ID)).thenReturn(e);

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThat(svc.findById(ID)).isSameAs(e);
    }

    @Test
    @DisplayName("findStalePendingForSweep() délègue au repo avec batchSize")
    void findStalePending() {
        CompensationLogRepository repo = mock(CompensationLogRepository.class);
        CompensationLogEntry e = entry("BF", "PENDING");
        when(repo.lockStalePendingBatch(100)).thenReturn(List.of(e));

        CompensationLogService svc = service(repo, new CompensationHandlerRegistry(List.of()));
        assertThatCode(() -> svc.findStalePendingForSweep(100)).doesNotThrowAnyException();
    }
}
