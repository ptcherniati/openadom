package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.monitoring.integrity.IntegrityService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DashboardIntegrityController}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("DashboardIntegrityController — unit tests")
class DashboardIntegrityControllerTest {

    @Mock private IntegrityService integrityService;
    @InjectMocks private DashboardIntegrityController controller;

    // ─── stagingVsFinal ───────────────────────────────────────────────────────

    @Test
    @DisplayName("stagingVsFinal() valeurs par défaut → 200")
    void stagingVsFinalDefaults() {
        when(integrityService.listIntegrity(24, 100)).thenReturn(List.of());
        ResponseEntity<List<IntegrityService.IntegrityRow>> r =
                controller.stagingVsFinal(24, 100);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEmpty();
    }

    @Test
    @DisplayName("stagingVsFinal() lookbackHours clampé à [1, 168]")
    void stagingVsFinalLookbackClamped() {
        when(integrityService.listIntegrity(168, 100)).thenReturn(List.of());
        controller.stagingVsFinal(9999, 100);
        verify(integrityService).listIntegrity(168, 100);

        when(integrityService.listIntegrity(1, 100)).thenReturn(List.of());
        controller.stagingVsFinal(0, 100);
        verify(integrityService).listIntegrity(1, 100);
    }

    @Test
    @DisplayName("stagingVsFinal() limit clampée à [1, 500]")
    void stagingVsFinalLimitClamped() {
        when(integrityService.listIntegrity(24, 500)).thenReturn(List.of());
        controller.stagingVsFinal(24, 9999);
        verify(integrityService).listIntegrity(24, 500);
    }

    // ─── reprocess ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("reprocess() → 200 avec résultat du service")
    void reprocess() {
        UUID corrId = UUID.randomUUID();
        IntegrityService.ReprocessResult result = mock(IntegrityService.ReprocessResult.class);
        when(integrityService.reprocess(corrId)).thenReturn(result);
        ResponseEntity<IntegrityService.ReprocessResult> r = controller.reprocess(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(result);
    }

    // ─── deletePreview ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deletePreview() → 200 avec résultat du service")
    void deletePreview() {
        UUID corrId = UUID.randomUUID();
        IntegrityService.DeletePreview preview = mock(IntegrityService.DeletePreview.class);
        when(integrityService.deletePreview(corrId)).thenReturn(preview);
        ResponseEntity<IntegrityService.DeletePreview> r = controller.deletePreview(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(preview);
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() → 200 avec résultat du service")
    void delete() {
        UUID corrId = UUID.randomUUID();
        IntegrityService.DeleteResult result = mock(IntegrityService.DeleteResult.class);
        when(integrityService.deleteWorkflow(corrId)).thenReturn(result);
        ResponseEntity<IntegrityService.DeleteResult> r = controller.delete(corrId);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(result);
    }
}
