package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.monitoring.integrity.IntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints admin oa-live pour la page "Intégrité" - sous-onglet
 * "Cohérence Staging vs Final" .
 *
 * @author R.YAHIAOUI
 */
@RestController
@RequestMapping("/api/dashboard/integrity")
@RequiredArgsConstructor
@Tag(name = "Dashboard Integrity",
     description = "Coherence staging vs final pour les imports cascade ( admin only )")
@SecurityRequirement(name = "bearerAuth")
public class DashboardIntegrityController {

    private final IntegrityService integrityService;

    @Operation(summary = "Liste les workflows recents avec leur status d'integrite")
    @GetMapping("/staging-vs-final")
    public ResponseEntity<List<IntegrityService.IntegrityRow>> stagingVsFinal(
            @RequestParam(required = false, defaultValue = "24") int lookbackHours,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        return ResponseEntity.ok(integrityService.listIntegrity(
                Math.max(1, Math.min(168, lookbackHours)),  // max 7 jours
                Math.max(1, Math.min(500, limit))));
    }

    @Operation(summary = "Re-execute UPSERT staging -> final pour 1 workflow ( v1 : non implemente )")
    @PostMapping("/{correlationId}/reprocess")
    public ResponseEntity<IntegrityService.ReprocessResult> reprocess(
            @PathVariable UUID correlationId) {
        return ResponseEntity.ok(integrityService.reprocess(correlationId));
    }
}
