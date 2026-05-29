package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link WorkflowLogRepository} — chemins sans DB
 * (null-guards, retentionDays check, beat exception handling).
 * Dépendances mockées via Mockito.
 */
@Tag("domain.model")
@DisplayName("WorkflowLogRepository — null-guards et branches pures")
class WorkflowLogRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private WorkflowLogRepository repo;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repo = new WorkflowLogRepository(jdbcTemplate);
    }

    private WorkflowLogEntry entry() {
        return new WorkflowLogEntry(
                UUID.randomUUID(), WorkflowLogEntry.TYPE_IMPORT, UUID.randomUUID(), "alice",
                "app1", "data1", "file.csv",
                Instant.now(), Instant.now(), Duration.ofSeconds(1),
                WorkflowLogEntry.STATUS_COMPLETED, 100L, 0L, 1, 1024L,
                List.of(), null);
    }

    // ─── insertBatch ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("insertBatch()")
    class InsertBatchTest {

        @Test
        @DisplayName("insertBatch(null) retourne 0 sans appeler le jdbcTemplate")
        void nullReturnsZero() {
            int result = repo.insertBatch(null);
            assertThat(result).isZero();
            verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(), anyInt(), any());
        }

        @Test
        @DisplayName("insertBatch(empty) retourne 0 sans appeler le jdbcTemplate")
        void emptyReturnsZero() {
            int result = repo.insertBatch(Collections.emptyList());
            assertThat(result).isZero();
            verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(), anyInt(), any());
        }

        @Test
        @DisplayName("insertBatch() compte uniquement les rows > 0")
        void countsOnlyPositiveResults() {
            // Simule batchUpdate retournant [[1, 0, 1]] : 2 insertions effectives
            when(jdbcTemplate.batchUpdate(any(String.class), any(), anyInt(), any()))
                    .thenReturn(new int[][]{{1, 0, 1}});

            int result = repo.insertBatch(List.of(entry(), entry(), entry()));
            assertThat(result).isEqualTo(2);
        }
    }

    // ─── recordStart ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordStart(null) retourne false sans appeler le jdbcTemplate")
    void recordStartNullReturnsFalse() {
        boolean result = repo.recordStart(null);
        assertThat(result).isFalse();
        verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Boolean.class), any());
    }

    @Test
    @DisplayName("recordStart() retourne true quand queryForObject retourne TRUE")
    void recordStartTrueWhenInserted() {
        // 10 vararg params: correlationId, workflowType, userId, userLogin,
        // applicationName, dataType, resourceName, startTime, bytesTotal, metadata
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Boolean.TRUE);
        WorkflowLogEntry start = new WorkflowLogEntry(
                UUID.randomUUID(), WorkflowLogEntry.TYPE_IMPORT, UUID.randomUUID(), "bob",
                "app2", "data2", "f.csv",
                Instant.now(), null, null,
                WorkflowLogEntry.STATUS_IN_PROGRESS, 0L, 0L, 0, 0L,
                List.of(), null);

        boolean result = repo.recordStart(start);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("recordStart() retourne false quand queryForObject retourne null (duplicate)")
    void recordStartFalseWhenNull() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        WorkflowLogEntry start = new WorkflowLogEntry(
                UUID.randomUUID(), WorkflowLogEntry.TYPE_IMPORT, UUID.randomUUID(), "charlie",
                "app3", "data3", "g.csv",
                Instant.now(), null, null,
                WorkflowLogEntry.STATUS_IN_PROGRESS, 0L, 0L, 0, 0L,
                List.of(), null);

        boolean result = repo.recordStart(start);
        assertThat(result).isFalse();
    }

    // ─── beat ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("beat()")
    class BeatTest {

        @Test
        @DisplayName("beat(null) retourne false sans appeler le jdbcTemplate")
        void nullReturnsFalse() {
            boolean result = repo.beat(null);
            assertThat(result).isFalse();
            verify(jdbcTemplate, never()).queryForObject(any(String.class), eq(Boolean.class), any());
        }

        @Test
        @DisplayName("beat() retourne true quand DB retourne TRUE")
        void beatTrueWhenUpdated() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(UUID.class)))
                    .thenReturn(Boolean.TRUE);
            boolean result = repo.beat(UUID.randomUUID());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("beat() retourne false quand DB retourne null")
        void beatFalseWhenNull() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(UUID.class)))
                    .thenReturn(null);
            boolean result = repo.beat(UUID.randomUUID());
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("beat() retourne false silencieusement quand RuntimeException levée (best-effort)")
        void beatFalseOnException() {
            when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(UUID.class)))
                    .thenThrow(new RuntimeException("DB transient error"));
            boolean result = repo.beat(UUID.randomUUID());
            assertThat(result).isFalse();
        }
    }

    // ─── markZombies ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("markZombies() retourne 0 quand queryForObject retourne null")
    void markZombiesNullSafe() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyInt()))
                .thenReturn(null);
        int result = repo.markZombies(5);
        assertThat(result).isZero();
    }

    @Test
    @DisplayName("markZombies() retourne la valeur DB quand non-null")
    void markZombiesReturnsDbValue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyInt()))
                .thenReturn(3);
        int result = repo.markZombies(5);
        assertThat(result).isEqualTo(3);
    }

    // ─── deleteOlderThan ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteOlderThan(0) retourne 0 sans appeler le jdbcTemplate")
    void deleteOlderThanZeroReturnsZero() {
        int result = repo.deleteOlderThan(0);
        assertThat(result).isZero();
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), (Object[]) any());
    }

    @Test
    @DisplayName("deleteOlderThan(-1) retourne 0 sans appeler le jdbcTemplate")
    void deleteOlderThanNegativeReturnsZero() {
        int result = repo.deleteOlderThan(-1);
        assertThat(result).isZero();
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), (Object[]) any());
    }

    @Test
    @DisplayName("deleteOlderThan(30) délègue au jdbcTemplate ( queryForObject : fonction SQL retourne un rowset )")
    void deleteOlderThanDelegates() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), (Object[]) any()))
                .thenReturn(5);
        int result = repo.deleteOlderThan(30);
        assertThat(result).isEqualTo(5);
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Integer.class), (Object[]) any());
    }

    @Test
    @DisplayName("deleteOlderThan(30) : queryForObject retourne null -> 0")
    void deleteOlderThanNullReturnsZero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), (Object[]) any()))
                .thenReturn(null);
        int result = repo.deleteOlderThan(30);
        assertThat(result).isZero();
    }
}
