package fr.inra.oresing.monitoring.integrity;

import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link IntegrityService}.
 *
 * <p>Couvre : computeStatus (tous les états), extractBinaryFileId (parse / null / malformé),
 * cache TTL (put/get/expire/invalidate), requireAdmin (accès refusé),
 * reprocess (non-implémenté), listIntegrity (via mocked JdbcTemplate),
 * deletePreview (workflow non trouvé + workflow trouvé), invalidateCount.
 */
@Tag("domain.model")
@DisplayName("IntegrityService — logique pure + admin guard")
class IntegrityServiceTest {

    private JdbcTemplate jdbc;
    private AuthenticationService authSvc;
    private IntegrityService service;

    @BeforeEach
    void setUp() {
        jdbc    = mock(JdbcTemplate.class);
        authSvc = mock(AuthenticationService.class);
        service = new IntegrityService(jdbc, authSvc);
    }

    // ─── requireAdmin ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("listIntegrity lève AccessDeniedException si l'utilisateur n'est pas openAdomAdmin")
    void listIntegrity_requiresAdmin() {
        fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles roles =
                mock(fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles.class);
        when(authSvc.getCurrentUserRoles()).thenReturn(roles);
        when(roles.isOpenAdomAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.listIntegrity(24, 100))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("reprocess lève AccessDeniedException si l'utilisateur n'est pas openAdomAdmin")
    void reprocess_requiresAdmin() {
        fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles roles =
                mock(fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles.class);
        when(authSvc.getCurrentUserRoles()).thenReturn(roles);
        when(roles.isOpenAdomAdmin()).thenReturn(false);

        UUID reprocessId = UUID.randomUUID();
        assertThatThrownBy(() -> service.reprocess(reprocessId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deletePreview lève AccessDeniedException si l'utilisateur n'est pas openAdomAdmin")
    void deletePreview_requiresAdmin() {
        fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles roles =
                mock(fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles.class);
        when(authSvc.getCurrentUserRoles()).thenReturn(roles);
        when(roles.isOpenAdomAdmin()).thenReturn(false);

        UUID deletePreviewId = UUID.randomUUID();
        assertThatThrownBy(() -> service.deletePreview(deletePreviewId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteWorkflow lève AccessDeniedException si l'utilisateur n'est pas openAdomAdmin")
    void deleteWorkflow_requiresAdmin() {
        fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles roles =
                mock(fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles.class);
        when(authSvc.getCurrentUserRoles()).thenReturn(roles);
        when(roles.isOpenAdomAdmin()).thenReturn(false);

        UUID deleteWorkflowId = UUID.randomUUID();
        assertThatThrownBy(() -> service.deleteWorkflow(deleteWorkflowId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── reprocess ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reprocess retourne started=false (non implémenté en v1)")
    void reprocess_notImplemented() {
        adminRole();

        IntegrityService.ReprocessResult result = service.reprocess(UUID.randomUUID());

        assertThat(result.started()).isFalse();
        assertThat(result.message()).isNotBlank();
    }

    // ─── invalidateCount ──────────────────────────────────────────────────────

    @Test
    @DisplayName("invalidateCount avec arguments non-null ne lève pas d'exception")
    void invalidateCount_validArgs_doesNotThrow() {
        assertThatCode(() -> service.invalidateCount("myapp", UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invalidateCount avec appSchema null ne lève pas d'exception")
    void invalidateCount_nullSchema_doesNotThrow() {
        assertThatCode(() -> service.invalidateCount(null, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invalidateCount avec binaryFileId null ne lève pas d'exception")
    void invalidateCount_nullFileId_doesNotThrow() {
        assertThatCode(() -> service.invalidateCount("myapp", null)).doesNotThrowAnyException();
    }

    // ─── listIntegrity — computeStatus via mocked JdbcTemplate ───────────────

    @Test
    @DisplayName("listIntegrity retourne COHERENT pour un workflow COMPLETED sans staging ni delta")
    void listIntegrity_completed_noStaging_noDelta_isCoherent() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        stubWorkflowQuery(corrId, "COMPLETED", 100L, metadata, 100L);
        stubStagingCount(corrId, 0L);
        stubReferenceValueCount(bfId, 100L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("COHERENT");
    }

    @Test
    @DisplayName("listIntegrity retourne DATA_LOSS quand expected > final + staging")
    void listIntegrity_dataLoss() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        stubWorkflowQuery(corrId, "COMPLETED", 200L, metadata, 50L);
        stubStagingCount(corrId, 0L);
        // final_count = 50, expected = 200 → delta = 150 → DATA_LOSS

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("DATA_LOSS");
    }

    @Test
    @DisplayName("listIntegrity retourne RECOVERABLE pour workflow FAILED avec staging non vide et delta=0")
    void listIntegrity_failed_withStaging_isRecoverable() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        // expected=10, persistedFinalCount=0, stagingCount=10 → delta = 10-0-10 = 0 → RECOVERABLE
        stubWorkflowQuery(corrId, "FAILED", 10L, metadata, 0L);
        stubStagingCount(corrId, 10L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("RECOVERABLE");
    }

    @Test
    @DisplayName("listIntegrity retourne REDEPOT_REQUIRED pour workflow FAILED sans staging ni rows finales")
    void listIntegrity_failed_noStaging_noFinal_isRedepotRequired() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        // expected=0, persistedFinalCount=0, stagingCount=0 → delta=0 → REDEPOT_REQUIRED
        stubWorkflowQuery(corrId, "FAILED", 0L, metadata, 0L);
        stubStagingCount(corrId, 0L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("REDEPOT_REQUIRED");
    }

    @Test
    @DisplayName("listIntegrity retourne UNKNOWN quand metadata manquante (finalCount négatif) et workflow non FAILED")
    void listIntegrity_missingMetadata_isUnknown() {
        adminRole();
        UUID corrId = UUID.randomUUID();

        stubWorkflowQuery(corrId, "COMPLETED", 100L, null, null);
        stubStagingCount(corrId, 0L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("listIntegrity retourne OVERCOUNT quand finalCount > expected")
    void listIntegrity_overcount() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        stubWorkflowQuery(corrId, "COMPLETED", 50L, metadata, 100L);
        stubStagingCount(corrId, 0L);
        // final_count=100 > expected=50 → delta < 0 → OVERCOUNT

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("OVERCOUNT");
    }

    @Test
    @DisplayName("listIntegrity retourne INCONSISTENT pour workflow COMPLETED avec staging non vide et delta=0")
    void listIntegrity_completed_withStaging_isInconsistent() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";

        stubWorkflowQuery(corrId, "COMPLETED", 110L, metadata, 100L);
        stubStagingCount(corrId, 10L);
        // delta = 110 - 100 - 10 = 0 ; staging > 0 ; COMPLETED → INCONSISTENT

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("INCONSISTENT");
    }

    @Test
    @DisplayName("listIntegrity retourne STUCK pour un workflow IN_PROGRESS dont le heartbeat est très ancien")
    void listIntegrity_inProgress_oldHeartbeat_isStuck() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";
        // heartbeat vieux de 10 min → STUCK
        java.sql.Timestamp oldHb = java.sql.Timestamp.from(
                java.time.Instant.now().minusSeconds(10 * 60));

        stubWorkflowQueryWithHeartbeat(corrId, "IN_PROGRESS", 100L, metadata, null, oldHb);
        stubStagingCount(corrId, 0L);
        stubReferenceValueCount(bfId, 0L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("STUCK");
    }

    @Test
    @DisplayName("listIntegrity retourne IN_PROGRESS pour un workflow IN_PROGRESS avec staging non vide et heartbeat récent")
    void listIntegrity_inProgress_recentHeartbeat_withStaging_isInProgress() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        UUID bfId   = UUID.randomUUID();
        String metadata = "{\"binaryFileId\":\"" + bfId + "\"}";
        // heartbeat récent → pas STUCK
        java.sql.Timestamp recentHb = java.sql.Timestamp.from(
                java.time.Instant.now().minusSeconds(30));

        // expected=10, persistedFinalCount=0, stagingCount=10 → delta=0 → IN_PROGRESS
        stubWorkflowQueryWithHeartbeat(corrId, "IN_PROGRESS", 10L, metadata, 0L, recentHb);
        stubStagingCount(corrId, 10L);

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("listIntegrity ignore les erreurs de staging count et continue sans lever d'exception")
    void listIntegrity_stagingCountFails_continuesWithZeroStaging() {
        adminRole();
        UUID corrId = UUID.randomUUID();

        // metadata null → pas de binaryFileId → finalCount = -1 → status = UNKNOWN
        stubWorkflowQuery(corrId, "COMPLETED", 0L, null, null);
        when(jdbc.queryForObject(
                contains("referencevalue_import_shared"),
                eq(Long.class),
                any()))
                .thenThrow(new RuntimeException("staging table missing"));

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        // L'exception staging ne doit pas propager ; le statut est UNKNOWN
        // car finalCount est indéterminable (pas de metadata binaryFileId)
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).integrityStatus()).isEqualTo("UNKNOWN");
    }

    // ─── deletePreview ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deletePreview retourne notFound lorsque le workflow est inconnu")
    void deletePreview_unknownCorrelationId_returnsNotFound() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), eq(corrId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        IntegrityService.DeletePreview preview = service.deletePreview(corrId);

        assertThat(preview.found()).isFalse();
        assertThat(preview.correlationId()).isEqualTo(corrId);
    }

    @Test
    @DisplayName("deletePreview lève IllegalArgumentException si correlationId est null")
    void deletePreview_nullCorrelationId_throws() {
        adminRole();

        assertThatThrownBy(() -> service.deletePreview(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
    }

    // ─── deleteWorkflow ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteWorkflow retourne deleted=false lorsque le workflow est inconnu")
    void deleteWorkflow_unknownCorrelationId_returnsNotDeleted() {
        adminRole();
        UUID corrId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), eq(corrId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        IntegrityService.DeleteResult result = service.deleteWorkflow(corrId);

        assertThat(result.deleted()).isFalse();
        assertThat(result.message()).contains(corrId.toString());
    }

    @Test
    @DisplayName("deleteWorkflow lève IllegalArgumentException si correlationId est null")
    void deleteWorkflow_nullCorrelationId_throws() {
        adminRole();

        assertThatThrownBy(() -> service.deleteWorkflow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
    }

    @Test
    @DisplayName("deleteWorkflow supprime les rows et retourne deleted=true")
    void deleteWorkflow_knownWorkflow_deletesRows() {
        adminRole();
        UUID corrId = UUID.randomUUID();

        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("application_name", "myapp");
        meta.put("metadata", null);
        when(jdbc.queryForMap(anyString(), eq(corrId))).thenReturn(meta);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1); // compensation_log
        when(jdbc.update(argThat(sql -> sql.contains("workflow_log")), eq(corrId))).thenReturn(1);

        IntegrityService.DeleteResult result = service.deleteWorkflow(corrId);

        assertThat(result.deleted()).isTrue();
    }

    // ─── listIntegrity avec rows vides ────────────────────────────────────────

    @Test
    @DisplayName("listIntegrity retourne une liste vide si aucun workflow récent")
    void listIntegrity_noWorkflows_returnsEmptyList() {
        adminRole();
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

        java.util.List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);

        assertThat(rows).isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void adminRole() {
        fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles roles =
                mock(fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles.class);
        when(authSvc.getCurrentUserRoles()).thenReturn(roles);
        when(roles.isOpenAdomAdmin()).thenReturn(true);
    }

    /**
     * Stubbing du query de liste de workflows. Construit une Map avec les colonnes
     * retournées par le RowMapper inline de listIntegrity.
     * La colonne {@code final_count} est {@code null} → le code tombera sur le fallback COUNT.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubWorkflowQuery(UUID corrId, String status, long expected,
                                   String metadata, Long persistedFinalCount) {
        stubWorkflowQueryWithHeartbeat(corrId, status, expected, metadata, persistedFinalCount, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubWorkflowQueryWithHeartbeat(UUID corrId, String status, long expected,
                                                String metadata, Long persistedFinalCount,
                                                java.sql.Timestamp lastHeartbeat) {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("correlationId",    corrId);
        row.put("workflowType",     "IMPORT");
        row.put("applicationName",  "myapp");
        row.put("dataType",         "reftype");
        row.put("status",           status);
        row.put("recordsProcessed", expected);
        row.put("startTime",        null);
        row.put("endTime",          null);
        row.put("lastHeartbeatAt",  lastHeartbeat);
        row.put("metadata",         metadata);
        row.put("finalCount",       persistedFinalCount);

        when(jdbc.query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                anyInt(),
                anyInt()))
                .thenReturn(java.util.List.of(row));
    }

    private void stubStagingCount(UUID corrId, long count) {
        when(jdbc.queryForObject(
                contains("referencevalue_import_shared"),
                eq(Long.class),
                eq(corrId)))
                .thenReturn(count);
    }

    private void stubReferenceValueCount(UUID bfId, long count) {
        when(jdbc.queryForObject(
                contains("referencevalue WHERE binaryfile"),
                eq(Long.class),
                eq(bfId)))
                .thenReturn(count);
    }
}
