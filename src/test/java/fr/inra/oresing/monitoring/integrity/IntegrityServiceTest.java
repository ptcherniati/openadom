package fr.inra.oresing.monitoring.integrity;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link IntegrityService}.
 * Toutes les dépendances JDBC sont mockées via Mockito.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("IntegrityService — unit tests")
class IntegrityServiceTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private AuthenticationService authenticationService;
    @InjectMocks private IntegrityService service;

    private static CurrentUserRoles admin() {
        return new CurrentUserRoles(List.of("openAdomAdmin"), false, null);
    }

    private static CurrentUserRoles regular() {
        return new CurrentUserRoles(List.of("user"), false, null);
    }

    // ─── reprocess ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reprocess()")
    class ReprocessTest {

        @Test
        @DisplayName("Admin → retourne ReprocessResult(started=false) avec message")
        void reprocessAdminNotImplemented() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
            IntegrityService.ReprocessResult r = service.reprocess(UUID.randomUUID());
            assertThat(r.started()).isFalse();
            assertThat(r.message()).isNotBlank();
        }

        @Test
        @DisplayName("Non-admin → AccessDeniedException")
        void reprocessNonAdmin() {
            when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
            assertThatThrownBy(() -> service.reprocess(UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ─── invalidateCount ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("invalidateCount()")
    class InvalidateCountTest {

        @Test
        @DisplayName("invalidateCount(null, id) → no-op sans exception")
        void nullSchema() {
            service.invalidateCount(null, UUID.randomUUID());
        }

        @Test
        @DisplayName("invalidateCount(schema, null) → no-op sans exception")
        void nullBinaryFileId() {
            service.invalidateCount("myapp", null);
        }

        @Test
        @DisplayName("invalidateCount(schema, id) → idempotent sans exception")
        void validArgs() {
            UUID id = UUID.randomUUID();
            service.invalidateCount("myapp", id);
            service.invalidateCount("myapp", id);
        }
    }

    // ─── listIntegrity — guard admin ──────────────────────────────────────────

    @Test
    @DisplayName("listIntegrity() non-admin → AccessDeniedException")
    void listIntegrityNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> service.listIntegrity(24, 100))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("listIntegrity() admin, aucun workflow → liste vide")
    @SuppressWarnings("unchecked")
    void listIntegrityEmpty() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(jdbc.query(anyString(), any(RowMapper.class), anyInt(), anyInt()))
                .thenReturn(List.of());

        List<IntegrityService.IntegrityRow> rows = service.listIntegrity(24, 100);
        assertThat(rows).isEmpty();
    }

    // ─── deletePreview — guard admin ─────────────────────────────────────────

    @Test
    @DisplayName("deletePreview() non-admin → AccessDeniedException")
    void deletePreviewNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> service.deletePreview(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deletePreview(null) → IllegalArgumentException")
    void deletePreviewNull() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        assertThatThrownBy(() -> service.deletePreview(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deletePreview() workflow introuvable → DeletePreview(found=false)")
    void deletePreviewNotFound() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(jdbc.queryForMap(anyString(), any(UUID.class)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        UUID corrId = UUID.randomUUID();
        IntegrityService.DeletePreview preview = service.deletePreview(corrId);
        assertThat(preview.found()).isFalse();
        assertThat(preview.correlationId()).isEqualTo(corrId);
    }

    // ─── deleteWorkflow — guard admin ────────────────────────────────────────

    @Test
    @DisplayName("deleteWorkflow() non-admin → AccessDeniedException")
    void deleteWorkflowNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> service.deleteWorkflow(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteWorkflow(null) → IllegalArgumentException")
    void deleteWorkflowNull() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        assertThatThrownBy(() -> service.deleteWorkflow(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteWorkflow() workflow introuvable → DeleteResult(deleted=false)")
    void deleteWorkflowNotFound() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(jdbc.queryForMap(anyString(), any(UUID.class)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        IntegrityService.DeleteResult result = service.deleteWorkflow(UUID.randomUUID());
        assertThat(result.deleted()).isFalse();
    }

    // ─── IntegrityRow record ──────────────────────────────────────────────────

    @Test
    @DisplayName("IntegrityRow record — constructeur et accesseurs")
    void integrityRowRecord() {
        UUID corrId = UUID.randomUUID();
        IntegrityService.IntegrityRow row = new IntegrityService.IntegrityRow(
                corrId, "myapp", "referenceType", "COMPLETED",
                1000L, 1000L, 0L, 0L, "COHERENT");

        assertThat(row.correlationId()).isEqualTo(corrId);
        assertThat(row.applicationName()).isEqualTo("myapp");
        assertThat(row.integrityStatus()).isEqualTo("COHERENT");
        assertThat(row.delta()).isEqualTo(0L);
    }

    // ─── ReprocessResult record ───────────────────────────────────────────────

    @Test
    @DisplayName("ReprocessResult record — constructeur et accesseurs")
    void reprocessResultRecord() {
        IntegrityService.ReprocessResult r = new IntegrityService.ReprocessResult(true, "done");
        assertThat(r.started()).isTrue();
        assertThat(r.message()).isEqualTo("done");
    }

    // ─── DeletePreview.notFound ───────────────────────────────────────────────

    @Test
    @DisplayName("DeletePreview.notFound() → found=false, tous les counts à 0")
    void deletePreviewNotFoundFactory() {
        UUID cid = UUID.randomUUID();
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(jdbc.queryForMap(anyString(), any(UUID.class)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        IntegrityService.DeletePreview p = service.deletePreview(cid);
        assertThat(p.found()).isFalse();
        assertThat(p.referenceValueRows()).isEqualTo(0L);
        assertThat(p.stagingRows()).isEqualTo(0L);
        assertThat(p.workflowLogRows()).isEqualTo(0L);
    }

    // ─── DeleteResult record ─────────────────────────────────────────────────

    @Test
    @DisplayName("DeleteResult record — constructeur et accesseurs")
    void deleteResultRecord() {
        IntegrityService.DeleteResult r = new IntegrityService.DeleteResult(
                true, 1L, 100L, 1L, 0L, 2L, "OK");
        assertThat(r.deleted()).isTrue();
        assertThat(r.workflowLogDeleted()).isEqualTo(1L);
        assertThat(r.referenceValueDeleted()).isEqualTo(100L);
        assertThat(r.message()).isEqualTo("OK");
    }
}
