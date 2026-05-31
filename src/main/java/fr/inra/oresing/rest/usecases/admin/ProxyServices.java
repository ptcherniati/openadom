package fr.inra.oresing.rest.usecases.admin;

import java.util.List;

/**
 * Source unique de vérité des <b>services exposés par le reverse-proxy</b>.
 *
 * <p>Cette liste DOIT rester alignée avec :
 * <ul>
 *   <li>les {@code location} de {@code infra/proxy/nginx.conf} ( un par service ) ;</li>
 *   <li>la liste {@code SERVICES} de l'entrypoint du proxy ( {@code vpn-boot.sh} ).</li>
 * </ul>
 *
 * <p>Partagée par les fonctionnalités d'exploitation « par service » : accès VPN
 * ( {@link VpnAccessService} ) et mode maintenance ( {@link MaintenanceModeService} ).
 * Un seul endroit à modifier pour ajouter / retirer un service - pas de
 * duplication ni de désynchronisation entre fonctionnalités.
 */
public final class ProxyServices {

    /** Services connus, dans l'ordre d'affichage IHM. Immuable. */
    public static final List<String> NAMES = List.of(
            "frontend", "backend", "grafana", "pgadmin",
            "oa-live", "actuator", "shiny", "postgrest", "graphql");

    private ProxyServices() {
    }
}
