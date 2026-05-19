package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.monitoring.session.JwtBlacklistRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DashboardController}.
 * Vérifie que chaque endpoint délègue correctement au service
 * et retourne le bon code HTTP.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("DashboardController — unit tests")
class DashboardControllerTest {

    @Mock private DashboardService service;
    @InjectMocks private DashboardController controller;

    // ─── inProgress ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("inProgress() → 200 avec la liste du service")
    void inProgress() {
        when(service.listInProgress()).thenReturn(List.of());
        ResponseEntity<List<DashboardWorkflowDTO>> r = controller.inProgress();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
        verify(service).listInProgress();
    }

    // ─── history ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("history() → 200 avec la page du service")
    void history() {
        DashboardWorkflowDTO.Page page = new DashboardWorkflowDTO.Page(List.of(), 0L, 100, 0);
        when(service.listHistory(null, null, null, null, null, null, true)).thenReturn(page);
        ResponseEntity<DashboardWorkflowDTO.Page> r =
                controller.history(null, null, null, null, null, null, true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(page);
    }

    @Test
    @DisplayName("history() avec filtres → délègue tous les args au service")
    void historyWithFilters() {
        DashboardWorkflowDTO.Page page = new DashboardWorkflowDTO.Page(List.of(), 5L, 10, 0);
        when(service.listHistory(10, 0, "IMPORT", "COMPLETED", "myapp", "alice", true))
                .thenReturn(page);
        ResponseEntity<DashboardWorkflowDTO.Page> r =
                controller.history(10, 0, "IMPORT", "COMPLETED", "myapp", "alice", true);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(page);
    }

    // ─── detail ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("detail() trouvé → 200")
    void detailFound() {
        UUID corrId = UUID.randomUUID();
        DashboardWorkflowDTO.Detail detail = mock(DashboardWorkflowDTO.Detail.class);
        when(service.findDetail(corrId)).thenReturn(Optional.of(detail));
        ResponseEntity<DashboardWorkflowDTO.Detail> r = controller.detail(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(detail);
    }

    @Test
    @DisplayName("detail() non trouvé → 404")
    void detailNotFound() {
        UUID corrId = UUID.randomUUID();
        when(service.findDetail(corrId)).thenReturn(Optional.empty());
        ResponseEntity<DashboardWorkflowDTO.Detail> r = controller.detail(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── pipeline ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pipeline() trouvé → 200")
    void pipelineFound() {
        UUID corrId = UUID.randomUUID();
        PipelineDTO dto = mock(PipelineDTO.class);
        when(service.pipeline(corrId)).thenReturn(Optional.of(dto));
        ResponseEntity<PipelineDTO> r = controller.pipeline(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("pipeline() non trouvé → 404")
    void pipelineNotFound() {
        UUID corrId = UUID.randomUUID();
        when(service.pipeline(corrId)).thenReturn(Optional.empty());
        assertThat(controller.pipeline(corrId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── finalizeProgress ────────────────────────────────────────────────────

    @Test
    @DisplayName("finalizeProgress() trouvé → 200")
    void finalizeProgressFound() {
        UUID corrId = UUID.randomUUID();
        FinalizeProgressDTO dto = mock(FinalizeProgressDTO.class);
        when(service.finalizeProgress(corrId)).thenReturn(Optional.of(dto));
        assertThat(controller.finalizeProgress(corrId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("finalizeProgress() non trouvé → 404")
    void finalizeProgressNotFound() {
        UUID corrId = UUID.randomUUID();
        when(service.finalizeProgress(corrId)).thenReturn(Optional.empty());
        assertThat(controller.finalizeProgress(corrId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── finalizeAggregate ───────────────────────────────────────────────────

    @Test
    @DisplayName("finalizeAggregate() → 200")
    void finalizeAggregate() {
        FinalizeAggregateDTO agg = new FinalizeAggregateDTO(0, 0, 0, 0, 0L, 0L, 0L, 0L);
        when(service.finalizeAggregate()).thenReturn(agg);
        ResponseEntity<FinalizeAggregateDTO> r = controller.finalizeAggregate();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(agg);
    }

    // ─── cascadePools ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cascadePools() → 200")
    void cascadePools() {
        PipelinePoolsDTO pools = mock(PipelinePoolsDTO.class);
        when(service.cascadePools()).thenReturn(pools);
        assertThat(controller.cascadePools().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── sessionsActive ──────────────────────────────────────────────────────

    @Test
    @DisplayName("sessionsActive() → 200 liste vide")
    void sessionsActive() {
        when(service.listActiveSessions()).thenReturn(List.of());
        ResponseEntity<List<SessionDTO>> r = controller.sessionsActive();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    // ─── sessionsHistory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sessionsHistory() → 200")
    void sessionsHistory() {
        SessionDTO.Page page = new SessionDTO.Page(List.of(), 0L, 100, 0);
        when(service.listSessionsHistory(null, null, null, null)).thenReturn(page);
        ResponseEntity<SessionDTO.Page> r = controller.sessionsHistory(null, null, null, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("sessionsHistory() avec filtres → délègue au service")
    void sessionsHistoryWithFilters() {
        SessionDTO.Page page = new SessionDTO.Page(List.of(), 0L, 50, 10);
        when(service.listSessionsHistory(50, 10, "alice", "LOGOUT")).thenReturn(page);
        ResponseEntity<SessionDTO.Page> r = controller.sessionsHistory(50, 10, "alice", "LOGOUT");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── disconnectSession ───────────────────────────────────────────────────

    @Test
    @DisplayName("disconnectSession() succès → 204")
    void disconnectSessionSuccess() {
        UUID sid = UUID.randomUUID();
        doNothing().when(service).disconnectSession(sid);
        ResponseEntity<Void> r = controller.disconnectSession(sid);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("disconnectSession() session inconnue → 404")
    void disconnectSessionNotFound() {
        UUID sid = UUID.randomUUID();
        doThrow(new NoSuchElementException("not found")).when(service).disconnectSession(sid);
        ResponseEntity<Void> r = controller.disconnectSession(sid);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── blacklist ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listBlacklist() → 200")
    void listBlacklist() {
        when(service.listBlacklist()).thenReturn(List.of());
        ResponseEntity<List<JwtBlacklistRegistry.Entry>> r = controller.listBlacklist();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("removeBlacklistEntry() trouvé → 204")
    void removeBlacklistEntryFound() {
        when(service.removeBlacklistEntry("hash123")).thenReturn(true);
        assertThat(controller.removeBlacklistEntry("hash123").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("removeBlacklistEntry() non trouvé → 404")
    void removeBlacklistEntryNotFound() {
        when(service.removeBlacklistEntry("hash999")).thenReturn(false);
        assertThat(controller.removeBlacklistEntry("hash999").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("clearBlacklist() → 200 avec count")
    void clearBlacklist() {
        when(service.clearBlacklist()).thenReturn(3);
        ResponseEntity<java.util.Map<String, Integer>> r = controller.clearBlacklist();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("cleared", 3);
    }

    // ─── cancel ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel() → 200 avec signalled=true")
    void cancelSuccess() {
        UUID corrId = UUID.randomUUID();
        DashboardService.CancelResult result = new DashboardService.CancelResult(true);
        when(service.cancelWorkflow(corrId)).thenReturn(result);
        ResponseEntity<DashboardService.CancelResult> r = controller.cancel(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(result);
    }

    @Test
    @DisplayName("cancel() workflow introuvable → 404")
    void cancelNotFound() {
        UUID corrId = UUID.randomUUID();
        when(service.cancelWorkflow(corrId)).thenThrow(new NoSuchElementException("not found"));
        ResponseEntity<DashboardService.CancelResult> r = controller.cancel(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
