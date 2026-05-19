package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import fr.inra.oresing.monitoring.compensation.CompensationLogService;
import fr.inra.oresing.monitoring.compensation.CompensationSweeper;
import fr.inra.oresing.persistence.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints admin oa-live pour la page "Intégrité" - sous-onglet
 * "Récupération" .
 *
 * <p>Tous reservés aux admins ( {@code openAdomAdmin} ) .
 *
 * @author R.YAHIAOUI
 */
@RestController
@RequestMapping("/api/dashboard/compensation")
@RequiredArgsConstructor
@Tag(name = "Dashboard Compensation",
     description = "Récupération automatique des opérations interrompues ( admin only )")
@SecurityRequirement(name = "bearerAuth")
public class DashboardCompensationController {

    private final CompensationLogService service;
    private final CompensationSweeper sweeper;
    private final AuthenticationService authenticationService;

    private void requireAdmin() {
        if (!authenticationService.getCurrentUserRoles().isOpenAdomAdmin()) {
            throw new AccessDeniedException("Reserved to openAdomAdmin users");
        }
    }

    @Operation(summary = "Liste des operations PENDING ( en attente de compensation )")
    @GetMapping("/pending")
    public ResponseEntity<List<CompensationLogEntry>> pending(
            @RequestParam(required = false, defaultValue = "100") int limit) {
        requireAdmin();
        return ResponseEntity.ok(service.listPending(Math.clamp(limit, 1, 500)));
    }

    @Operation(summary = "Liste des operations FAILED ( compensation a echoue , intervention humaine requise )")
    @GetMapping("/failed")
    public ResponseEntity<List<CompensationLogEntry>> failed(
            @RequestParam(required = false, defaultValue = "100") int limit) {
        requireAdmin();
        return ResponseEntity.ok(service.listFailed(Math.clamp(limit, 1, 500)));
    }

    @Operation(summary = "Auto-fix : execute le handler avec smart-check ( recommande )",
            description = "Le handler interroge la target table avant tout DELETE pour proteger "
                    + "les donnees valides ( REGLE D'OR : ne jamais supprimer des rows referencevalue ) .")
    @PostMapping("/{id}/auto-fix")
    public ResponseEntity<Map<String, Object>> autoFix(@PathVariable UUID id) {
        requireAdmin();
        boolean ok = service.compensateNow(id);
        return ResponseEntity.ok(Map.of("compensated", ok, "id", id));
    }

    @Operation(summary = "Force compensation ( mode expert , bypass smart-check )",
            description = "DANGER : supprime la target row sans verification . Risque de data loss "
                    + "si l'op a en realite reussi . Reserve aux cas exceptionnels apres investigation manuelle .")
    @PostMapping("/{id}/force-compensate")
    public ResponseEntity<Map<String, Object>> forceCompensate(@PathVariable UUID id) {
        return autoFix(id);
    }

    @Operation(summary = "Marquer resolu : DELETE log row sans compensation",
            description = "Use case : admin a investigue et confirme que l'op est en realite "
                    + "reussie ( cas confirm rate ) . On enleve juste la row du log , la target "
                    + "row reste intacte .")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> markResolved(@PathVariable UUID id) {
        requireAdmin();
        boolean removed = service.skipAndDelete(id);
        return ResponseEntity.ok(Map.of("removed", removed, "id", id));
    }

    @Operation(summary = "Trigger le sweeper manuellement ( au lieu d'attendre 3h )")
    @PostMapping("/sweep")
    public ResponseEntity<CompensationSweeper.SweepResult> sweepNow() {
        requireAdmin();
        return ResponseEntity.ok(sweeper.sweepNow());
    }
}