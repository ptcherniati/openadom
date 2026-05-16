package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.config.ConfigChangeAudit;
import fr.inra.oresing.workflow.cascade.config.ConfigEditService;
import fr.inra.oresing.workflow.cascade.config.ConfigFieldRegistry;
import fr.inra.oresing.workflow.cascade.config.SinkConcurrencyEstimator;
import fr.inra.oresing.workflow.cascade.config.StrategyOptionsResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link DashboardConfigController}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("DashboardConfigController — unit tests")
class DashboardConfigControllerTest {

    @Mock private DashboardService service;
    @Mock private ConfigEditService configEditService;
    @Mock private StrategyOptionsResolver strategyOptionsResolver;
    @InjectMocks private DashboardConfigController controller;

    // ─── getConfig ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getConfig() → 200 avec DTO du service")
    void getConfig() {
        DashboardConfigDTO dto = mock(DashboardConfigDTO.class);
        when(service.getConfig()).thenReturn(dto);
        ResponseEntity<DashboardConfigDTO> r = controller.getConfig();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(dto);
    }

    // ─── patchImport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("patchImport() → 200 avec résultat du service")
    void patchImport() {
        ConfigEditService.PatchResult result = mock(ConfigEditService.PatchResult.class);
        Map<String, Object> patch = Map.of("chunkSizeLines", 2000);
        when(configEditService.applyPatch(patch)).thenReturn(result);
        ResponseEntity<ConfigEditService.PatchResult> r = controller.patchImport(patch);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(result);
    }

    // ─── schema ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("schema() → 200 avec liste des métadonnées")
    void schema() {
        List<ConfigFieldRegistry.FieldMeta> meta = List.of();
        when(configEditService.schema()).thenReturn(meta);
        ResponseEntity<List<ConfigFieldRegistry.FieldMeta>> r = controller.schema();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isSameAs(meta);
    }

    // ─── audit ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("audit() → 200 avec liste des entrées")
    void audit() {
        List<ConfigChangeAudit.Entry> entries = List.of();
        when(configEditService.auditList()).thenReturn(entries);
        ResponseEntity<List<ConfigChangeAudit.Entry>> r = controller.audit();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── snapshot ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("snapshot() → 200 avec payload enrichi (snapshot, sinkEstimate, strategyOptions)")
    void snapshot() {
        // SinkConcurrencyEstimator.estimate() est pure/statique → on la laisse tourner réellement
        Map<String, Object> snap = Map.of("sinkStrategy", "MERGE_FILE", "chunkSizeLines", 1000);
        when(configEditService.snapshot()).thenReturn(snap);
        when(strategyOptionsResolver.resolve(any())).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> r = controller.snapshot();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("snapshot");
        assertThat(r.getBody()).containsKey("sinkEstimate");
        assertThat(r.getBody()).containsKey("strategyOptions");
    }

    // ─── preview ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("preview(null) → 200 basé sur snapshot uniquement")
    void previewNull() {
        Map<String, Object> snap = Map.of("sinkStrategy", "MERGE_FILE", "chunkSizeLines", 1000);
        when(configEditService.snapshot()).thenReturn(snap);
        when(strategyOptionsResolver.resolve(any())).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> r = controller.preview(null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsKey("snapshot");
    }

    @Test
    @DisplayName("preview(hypothetical) → 200, merge snapshot + hypothetical")
    void previewWithHypothetical() {
        Map<String, Object> snap = Map.of("sinkStrategy", "MERGE_FILE", "chunkSizeLines", 1000);
        Map<String, Object> hypo = Map.of("chunkSizeLines", 500);
        when(configEditService.snapshot()).thenReturn(snap);
        when(strategyOptionsResolver.resolve(any())).thenReturn(Map.of());

        ResponseEntity<Map<String, Object>> r = controller.preview(hypo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── handleConfigPatchError ───────────────────────────────────────────────

    @Test
    @DisplayName("handleConfigPatchError(IllegalArgumentException) → 400 VALIDATION_ERROR")
    void handleIllegalArgument() {
        ResponseEntity<Map<String, String>> r =
                controller.handleConfigPatchError(new IllegalArgumentException("bad value"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(r.getBody()).containsEntry("message", "bad value");
    }

    @Test
    @DisplayName("handleConfigPatchError(UnsupportedOperationException) → 400 FIELD_READ_ONLY")
    void handleUnsupportedOperation() {
        ResponseEntity<Map<String, String>> r =
                controller.handleConfigPatchError(new UnsupportedOperationException("read only"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("code", "FIELD_READ_ONLY");
    }

    @Test
    @DisplayName("handleConfigPatchError(NoSuchElementException) → 400 FIELD_UNKNOWN")
    void handleNoSuchElement() {
        ResponseEntity<Map<String, String>> r =
                controller.handleConfigPatchError(new NoSuchElementException("unknown field"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("code", "FIELD_UNKNOWN");
    }

    @Test
    @DisplayName("handleConfigPatchError(message=null) → message vide")
    void handleNullMessage() {
        ResponseEntity<Map<String, String>> r =
                controller.handleConfigPatchError(new IllegalArgumentException((String) null));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("message", "");
    }

    // ─── handleBadJson ────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleBadJson() → 400 BAD_REQUEST avec message")
    void handleBadJson() {
        // HttpMessageNotReadableException nécessite un argument non-null
        org.springframework.mock.http.MockHttpInputMessage msg =
                new org.springframework.mock.http.MockHttpInputMessage(new byte[0]);
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", new RuntimeException("cause"), msg);
        ResponseEntity<Map<String, String>> r = controller.handleBadJson(ex);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("code", "BAD_REQUEST");
        assertThat(r.getBody()).containsKey("message");
    }
}
