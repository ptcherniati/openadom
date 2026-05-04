package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link StagingCleanupHandler} . Couvre :
 *
 * <ul>
 *   <li>Smart-check : refuse cleanup si workflow_log.status = IN_PROGRESS</li>
 *   <li>SHARED_UNLOGGED : DELETE WHERE correlation_id</li>
 *   <li>PER_WORKFLOW_TABLE : DROP TABLE IF EXISTS</li>
 *   <li>Validation injection : reject schema / table avec caracteres exotiques</li>
 *   <li>Fallback payload absent : DELETE ( comportement le moins destructif )</li>
 *   <li>Idempotence : DELETE de 0 row OK , DROP IF EXISTS OK</li>
 * </ul>
 */
@DisplayName("StagingCleanupHandler")
class StagingCleanupHandlerTest {

    private JdbcTemplate           jdbc;
    private StagingCleanupHandler  handler;

    @BeforeEach
    void setUp() {
        jdbc    = mock(JdbcTemplate.class);
        handler = new StagingCleanupHandler(jdbc);
    }

    @Test
    @DisplayName("operationType ( ) retourne STAGING_CLEANUP")
    void op_type_is_staging_cleanup() {
        assertThat(handler.operationType()).isEqualTo("STAGING_CLEANUP");
    }

    @Test
    @DisplayName("smart-check : refuse cleanup si workflow encore IN_PROGRESS")
    void refuses_cleanup_if_workflow_in_progress() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn("IN_PROGRESS");

        CompensationLogEntry entry = entry(corrId, "SHARED_UNLOGGED",
                "oa_staging", "referencevalue_import_shared");

        assertThatThrownBy(() -> handler.compensate(entry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still IN_PROGRESS")
                .hasMessageContaining("zombie sweeper");

        // Aucun DELETE / DROP execute .
        verify(jdbc, never()).update(anyString(), eq(corrId.toString()));
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("SHARED_UNLOGGED : DELETE WHERE correlation_id quand workflow FAILED")
    void shared_unlogged_deletes_rows_by_correlation_id() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn("FAILED");
        when(jdbc.update(anyString(), eq(corrId.toString()))).thenReturn(42);

        CompensationLogEntry entry = entry(corrId, "SHARED_UNLOGGED",
                "oa_staging", "referencevalue_import_shared");

        handler.compensate(entry);

        verify(jdbc, times(1)).update(
                eq("DELETE FROM oa_staging.referencevalue_import_shared WHERE correlation_id = ?::uuid"),
                eq(corrId.toString()));
    }

    @Test
    @DisplayName("PER_WORKFLOW_TABLE : DROP TABLE IF EXISTS quand workflow CANCELLED")
    void per_workflow_table_drops_dedicated_table() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn("CANCELLED");

        CompensationLogEntry entry = entry(corrId, "PER_WORKFLOW_TABLE",
                "oa_staging", "referencevalue_import_abc12345");

        handler.compensate(entry);

        verify(jdbc, times(1)).execute(
                eq("DROP TABLE IF EXISTS oa_staging.referencevalue_import_abc12345"));
    }

    @Test
    @DisplayName("schema invalide ( injection ) leve IllegalArgumentException")
    void rejects_invalid_schema() {
        UUID corrId = UUID.randomUUID();
        CompensationLogEntry entry = entry(corrId, "SHARED_UNLOGGED",
                "oa_staging; DROP TABLE", "referencevalue_import_shared");

        assertThatThrownBy(() -> handler.compensate(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid target_schema");
    }

    @Test
    @DisplayName("table invalide ( injection ) leve IllegalArgumentException")
    void rejects_invalid_table() {
        UUID corrId = UUID.randomUUID();
        CompensationLogEntry entry = entry(corrId, "SHARED_UNLOGGED",
                "oa_staging", "rev'); DROP--");

        assertThatThrownBy(() -> handler.compensate(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid target_table");
    }

    @Test
    @DisplayName("payload sans stagingStrategy : fallback DELETE ( safest )")
    void missing_payload_falls_back_to_delete() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn("FAILED");
        when(jdbc.update(anyString(), eq(corrId.toString()))).thenReturn(0);

        CompensationLogEntry entry = new CompensationLogEntry(
                UUID.randomUUID(), "STAGING_CLEANUP",
                "oa_staging", "referencevalue_import_shared", corrId.toString(),
                corrId, UUID.randomUUID(), "tester",
                Map.of(),  // pas de stagingStrategy
                "PENDING", Instant.now(), 60, 0, null, null);

        handler.compensate(entry);

        verify(jdbc, times(1)).update(
                eq("DELETE FROM oa_staging.referencevalue_import_shared WHERE correlation_id = ?::uuid"),
                eq(corrId.toString()));
    }

    @Test
    @DisplayName("stagingStrategy inconnu leve IllegalArgumentException")
    void unknown_strategy_throws() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn("FAILED");

        CompensationLogEntry entry = entry(corrId, "EXOTIC_NEW_STRATEGY",
                "oa_staging", "tab");

        assertThatThrownBy(() -> handler.compensate(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown stagingStrategy");
    }

    @Test
    @DisplayName("workflow_log inconnu ( null status ) : continue le cleanup")
    void null_status_proceeds_with_cleanup() {
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(corrId)))
                .thenReturn(null);
        when(jdbc.update(anyString(), eq(corrId.toString()))).thenReturn(0);

        CompensationLogEntry entry = entry(corrId, "SHARED_UNLOGGED",
                "oa_staging", "referencevalue_import_shared");

        handler.compensate(entry);

        verify(jdbc, times(1)).update(anyString(), eq(corrId.toString()));
    }

    private CompensationLogEntry entry(UUID corrId, String stagingStrategy,
                                       String schema, String table) {
        return new CompensationLogEntry(
                UUID.randomUUID(), "STAGING_CLEANUP",
                schema, table, corrId.toString(),
                corrId, UUID.randomUUID(), "tester",
                Map.of("stagingStrategy", stagingStrategy,
                       "tableName", schema + "." + table,
                       "correlationId", corrId.toString()),
                "PENDING", Instant.now(), 60, 0, null, null);
    }
}
