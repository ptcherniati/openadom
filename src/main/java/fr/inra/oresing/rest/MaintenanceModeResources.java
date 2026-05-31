package fr.inra.oresing.rest;

import fr.inra.oresing.rest.usecases.admin.MaintenanceModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint d'administration du <b>mode maintenance</b> de l'instance ( global,
 * non lié à une application ). Bascule le drapeau lu par le reverse-proxy pour
 * afficher la page de maintenance et bloquer l'accès aux services.
 *
 * <p>Action d'exploitation, séparée de la logique métier — au même titre que les
 * autres ressources admin ( caches, sessions, stats système ). Gardée
 * {@code SYSTEM_OPENADOM_ADMIN}.
 *
 * <p><b>Anti-lockout</b> : cette route ( et l'appli oa-live ) DOIT être exemptée
 * du blocage dans la conf nginx, sinon, maintenance activée, plus aucun admin ne
 * pourrait la désactiver depuis l'UI. Le filtrage reste assuré par l'auth JWT
 * admin ci-dessous, pas par l'IP. Filet de secours hors-ligne : le script
 * {@code maintenance.sh off} ( touche le même drapeau côté hôte ).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@SecurityRequirement(name = "Bearer Authentication")
public class MaintenanceModeResources {

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceModeResources(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    /** État courant du mode maintenance. */
    public record MaintenanceStatus(boolean enabled) {
    }

    /** Demande de bascule. {@code reason} optionnelle ( tracée dans le drapeau ). */
    public record MaintenanceToggleRequest(boolean enabled, String reason) {
    }

    @Operation(summary = "État du mode maintenance")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public MaintenanceStatus status() {
        return new MaintenanceStatus(maintenanceModeService.isEnabled());
    }

    @Operation(summary = "Active / désactive le mode maintenance")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MaintenanceStatus toggle(@RequestBody MaintenanceToggleRequest request) {
        if (request.enabled()) {
            maintenanceModeService.enable(request.reason());
        } else {
            maintenanceModeService.disable();
        }
        return new MaintenanceStatus(maintenanceModeService.isEnabled());
    }
}
