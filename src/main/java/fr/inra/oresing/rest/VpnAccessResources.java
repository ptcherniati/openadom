package fr.inra.oresing.rest;

import fr.inra.oresing.rest.usecases.admin.VpnAccessService;
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

import java.util.List;
import java.util.Map;

/**
 * Endpoint d'administration de l'<b>accès VPN par service</b> ( global, non lié
 * à une application ). Bascule, par service exposé via nginx, l'exigence VPN
 * ( IP autorisée ) lue par le reverse-proxy à chaque requête.
 *
 * <p>Action d'exploitation, séparée de la logique métier - au même titre que
 * le mode maintenance ou la purge des caches. Gardée {@code SYSTEM_OPENADOM_ADMIN}.
 *
 * <p><b>Risque de lockout</b> : gater un service dont dépend l'administrateur
 * ( oa-live, backend ) alors qu'il n'est pas sur le VPN coupe son propre accès.
 * Le filet de secours hors-ligne est le script {@code vpn.sh} ( touche les
 * mêmes drapeaux côté hôte ).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/vpn")
@SecurityRequirement(name = "Bearer Authentication")
public class VpnAccessResources {

    private final VpnAccessService vpnAccessService;

    public VpnAccessResources(VpnAccessService vpnAccessService) {
        this.vpnAccessService = vpnAccessService;
    }

    /** État VPN d'un service : {@code gated=true} => VPN requis. */
    public record VpnServiceStatus(String service, boolean gated) {
    }

    /** Demande de bascule de l'accès VPN d'un service. */
    public record VpnToggleRequest(String service, boolean gated) {
    }

    private List<VpnServiceStatus> toList(Map<String, Boolean> states) {
        return states.entrySet().stream()
                .map(e -> new VpnServiceStatus(e.getKey(), e.getValue()))
                .toList();
    }

    @Operation(summary = "État de l'accès VPN par service")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VpnServiceStatus> status() {
        return toList(vpnAccessService.status());
    }

    @Operation(summary = "Active / désactive l'exigence VPN d'un service")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VpnServiceStatus> toggle(@RequestBody VpnToggleRequest request) {
        vpnAccessService.setGated(request.service(), request.gated());
        return toList(vpnAccessService.status());
    }
}
