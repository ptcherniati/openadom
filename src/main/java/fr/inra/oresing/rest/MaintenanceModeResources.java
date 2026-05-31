package fr.inra.oresing.rest;

import fr.inra.oresing.rest.usecases.admin.MaintenanceModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

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

    /**
     * Nom du cookie de contournement posé pour les admins quand l'accès admin
     * est autorisé en maintenance. nginx compare sa valeur au secret partagé.
     */
    private static final String BYPASS_COOKIE = "oa_maint_bypass";
    /** Durée de vie du cookie de contournement. */
    private static final Duration BYPASS_TTL = Duration.ofHours(12);

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceModeResources(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    /**
     * État courant du mode maintenance.
     *
     * @param enabled      maintenance active.
     * @param allowAdmins  accès admin autorisé ( cookie de contournement ) .
     */
    public record MaintenanceStatus(boolean enabled, boolean allowAdmins) {
    }

    /**
     * Demande de bascule.
     *
     * @param enabled      cible.
     * @param reason       optionnelle ( tracée dans le drapeau ) .
     * @param allowAdmins  laisser les admins accéder aux services en maintenance.
     */
    public record MaintenanceToggleRequest(boolean enabled, String reason, boolean allowAdmins) {
    }

    @Operation(summary = "État du mode maintenance")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public MaintenanceStatus status() {
        return currentStatus();
    }

    @Operation(summary = "Active / désactive le mode maintenance")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceStatus> toggle(@RequestBody MaintenanceToggleRequest request) {
        if (request.enabled()) {
            maintenanceModeService.enable(request.reason(), request.allowAdmins());
            // Si l'accès admin est autorisé , on pose immédiatement le cookie de
            // contournement sur la réponse : l'admin qui active la maintenance
            // peut tester l'appli aussitôt , depuis n'importe quelle IP.
            if (request.allowAdmins()) {
                return withBypassCookie(bypassCookie(), currentStatus());
            }
        } else {
            maintenanceModeService.disable();
        }
        // Désactivation ou activation sans accès admin : on efface tout cookie résiduel.
        return withBypassCookie(clearedBypassCookie(), currentStatus());
    }

    @Operation(summary = "Obtenir le cookie d'accès admin en maintenance")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/bypass-cookie", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MaintenanceStatus> acquireBypassCookie() {
        // Permet à un AUTRE admin ( que celui qui a activé ) d'obtenir le cookie
        // pour tester. Sans effet utile si l'accès admin n'est pas autorisé.
        ResponseCookie cookie = maintenanceModeService.isAdminBypassEnabled()
                ? bypassCookie()
                : clearedBypassCookie();
        return withBypassCookie(cookie, currentStatus());
    }

    private MaintenanceStatus currentStatus() {
        return new MaintenanceStatus(maintenanceModeService.isEnabled(),
                maintenanceModeService.isAdminBypassEnabled());
    }

    private ResponseEntity<MaintenanceStatus> withBypassCookie(ResponseCookie cookie, MaintenanceStatus body) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private ResponseCookie bypassCookie() {
        return baseCookie(maintenanceModeService.getBypassSecret(), BYPASS_TTL);
    }

    private ResponseCookie clearedBypassCookie() {
        return baseCookie("", Duration.ZERO);
    }

    private ResponseCookie baseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(BYPASS_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }
}
