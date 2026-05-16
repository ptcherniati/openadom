package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import fr.inra.oresing.monitoring.compensation.CompensationLogService;
import fr.inra.oresing.monitoring.compensation.CompensationSweeper;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DashboardCompensationController}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("DashboardCompensationController — unit tests")
class DashboardCompensationControllerTest {

    @Mock private CompensationLogService service;
    @Mock private CompensationSweeper sweeper;
    @Mock private AuthenticationService authenticationService;
    @InjectMocks private DashboardCompensationController controller;

    private static CurrentUserRoles admin() {
        return new CurrentUserRoles(List.of("openAdomAdmin"), false, null);
    }

    private static CurrentUserRoles regular() {
        return new CurrentUserRoles(List.of("someOtherRole"), false, null);
    }

    // ─── pending ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pending() admin → 200 avec liste")
    void pendingAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.listPending(100)).thenReturn(List.of());
        ResponseEntity<List<CompensationLogEntry>> r = controller.pending(100);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    @Test
    @DisplayName("pending() non-admin → AccessDeniedException")
    void pendingNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> controller.pending(100))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("pending() limite clampée à [1, 500]")
    void pendingClamp() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.listPending(500)).thenReturn(List.of());
        controller.pending(9999);
        verify(service).listPending(500);

        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.listPending(1)).thenReturn(List.of());
        controller.pending(-5);
        verify(service).listPending(1);
    }

    // ─── failed ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("failed() admin → 200 avec liste")
    void failedAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.listFailed(100)).thenReturn(List.of());
        ResponseEntity<List<CompensationLogEntry>> r = controller.failed(100);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("failed() non-admin → AccessDeniedException")
    void failedNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> controller.failed(100))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── autoFix ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("autoFix() admin → 200 avec compensated=true")
    void autoFixAdmin() {
        UUID id = UUID.randomUUID();
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.compensateNow(id)).thenReturn(true);
        ResponseEntity<Map<String, Object>> r = controller.autoFix(id);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("compensated", true);
        assertThat(r.getBody()).containsEntry("id", id);
    }

    @Test
    @DisplayName("autoFix() non-admin → AccessDeniedException")
    void autoFixNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> controller.autoFix(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── forceCompensate ─────────────────────────────────────────────────────

    @Test
    @DisplayName("forceCompensate() admin → 200 (même comportement que autoFix)")
    void forceCompensateAdmin() {
        UUID id = UUID.randomUUID();
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.compensateNow(id)).thenReturn(false);
        ResponseEntity<Map<String, Object>> r = controller.forceCompensate(id);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("compensated", false);
    }

    // ─── markResolved ────────────────────────────────────────────────────────

    @Test
    @DisplayName("markResolved() admin → 200 avec removed=true")
    void markResolvedAdmin() {
        UUID id = UUID.randomUUID();
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        when(service.skipAndDelete(id)).thenReturn(true);
        ResponseEntity<Map<String, Object>> r = controller.markResolved(id);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("removed", true);
    }

    @Test
    @DisplayName("markResolved() non-admin → AccessDeniedException")
    void markResolvedNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> controller.markResolved(UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── sweepNow ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepNow() admin → 200 avec résultat")
    void sweepNowAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(admin());
        CompensationSweeper.SweepResult result = mock(CompensationSweeper.SweepResult.class);
        when(sweeper.sweepNow()).thenReturn(result);
        ResponseEntity<CompensationSweeper.SweepResult> r = controller.sweepNow();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(result);
    }

    @Test
    @DisplayName("sweepNow() non-admin → AccessDeniedException")
    void sweepNowNonAdmin() {
        when(authenticationService.getCurrentUserRoles()).thenReturn(regular());
        assertThatThrownBy(() -> controller.sweepNow())
                .isInstanceOf(AccessDeniedException.class);
    }
}
