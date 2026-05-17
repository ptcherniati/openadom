package fr.inra.oresing.monitoring.compensation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link CompensationSweeper}.
 */
@Tag("domain.model")
@DisplayName("CompensationSweeper — filet de sécurité compensation_log")
class CompensationSweeperTest {

    private CompensationLogEntry entry(String opType) {
        return new CompensationLogEntry(
                UUID.randomUUID(), opType,
                "oa_data", "binary_file", "id-42",
                UUID.randomUUID(), UUID.randomUUID(), "alice",
                java.util.Map.of(),
                CompensationLogEntry.STATUS_PENDING,
                Instant.now(), CompensationLogEntry.DEFAULT_TTL_MINUTES,
                0, null, null);
    }

    // ─── sweepNow() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepNow() retourne 0 compensated quand aucune row stale")
    void sweepNowNoStale() {
        CompensationLogService service = mock(CompensationLogService.class);
        when(service.findStalePendingForSweep(anyInt())).thenReturn(List.of());
        when(service.deleteFailedOlderThan(anyInt())).thenReturn(0);

        CompensationSweeper sweeper = new CompensationSweeper(service);

        CompensationSweeper.SweepResult result = sweeper.sweepNow();

        assertThat(result.totalProcessed()).isZero();
        assertThat(result.compensated()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("sweepNow() compte correctement compensated / failed")
    void sweepNowWithResults() {
        CompensationLogService service = mock(CompensationLogService.class);
        CompensationLogEntry ok  = entry("TYPE_OK");
        CompensationLogEntry bad = entry("TYPE_FAIL");
        when(service.findStalePendingForSweep(anyInt())).thenReturn(List.of(ok, bad));
        when(service.runHandlerAndDelete(ok)).thenReturn(true);
        when(service.runHandlerAndDelete(bad)).thenReturn(false);

        CompensationSweeper sweeper = new CompensationSweeper(service);

        CompensationSweeper.SweepResult result = sweeper.sweepNow();

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.compensated()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.durationMs()).isNotNegative();
    }

    // ─── sweep() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweep() délègue à service.findStalePendingForSweep et runHandlerAndDelete")
    void sweepCallsService() {
        CompensationLogService service = mock(CompensationLogService.class);
        CompensationLogEntry e = entry("SOME_TYPE");
        when(service.findStalePendingForSweep(anyInt())).thenReturn(List.of(e));
        when(service.runHandlerAndDelete(e)).thenReturn(true);
        when(service.deleteFailedOlderThan(anyInt())).thenReturn(0);

        CompensationSweeper sweeper = new CompensationSweeper(service);
        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        verify(service).findStalePendingForSweep(anyInt());
        verify(service).runHandlerAndDelete(e);
    }

    // ─── SweepResult record ──────────────────────────────────────────────────

    @Test
    @DisplayName("SweepResult conserve tous les champs")
    void sweepResultRecord() {
        CompensationSweeper.SweepResult r = new CompensationSweeper.SweepResult(10, 8, 2, 350L);
        assertThat(r.totalProcessed()).isEqualTo(10);
        assertThat(r.compensated()).isEqualTo(8);
        assertThat(r.failed()).isEqualTo(2);
        assertThat(r.durationMs()).isEqualTo(350L);
    }

    @Test
    @DisplayName("SweepResult record equality")
    void sweepResultEquality() {
        CompensationSweeper.SweepResult a = new CompensationSweeper.SweepResult(5, 3, 2, 100L);
        CompensationSweeper.SweepResult b = new CompensationSweeper.SweepResult(5, 3, 2, 100L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
