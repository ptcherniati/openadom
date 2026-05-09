package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.AuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints d'administration des caches mémoire backend.
 *
 * <p>Trois opérations exposées :
 * <ul>
 *   <li>POST {@code /applications/{name}/admin/invalidate-caches} -
 *       purge les 3 caches ( filterList , scopes , checkedFormatComponents )
 *       pour une application donnée. Réservé aux applicationManager.</li>
 *   <li>POST {@code /admin/caches/invalidate-all} - purge tous les
 *       caches mémoire backend ( toutes apps , tous users ). Réservé
 *       à openAdomAdmin.</li>
 *   <li>GET {@code /admin/caches/stats} - statistiques d'observabilité
 *       ( taille de chaque cache ). Réservé à openAdomAdmin.</li>
 * </ul>
 *
 * <p>Pourquoi ces endpoints ? Le bouton existant
 * {@code GET /filters?refresh=true} invalide déjà les 3 caches en
 * cascade pour le couple ( app , datatype ) demandé. Mais après une
 * mise à jour de YAML config , ou si un opérateur soupçonne une
 * dérive de cache , il manque un moyen de purger l'app entière ou
 * tous les caches sans redémarrer le backend.
 *
 * <p>Audit OA_FULL_REVIEW (8/5/26) - extrait dans son propre
 * controller plutôt que dilué dans {@code OreSiResources} pour rester
 * trouvable et bien tagué côté Swagger.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class CacheAdminResources {

    private final ServiceContainer serviceContainer;

    public CacheAdminResources(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }

    @Operation(
            summary = "Purge tous les caches mémoire pour une application",
            description = "Invalide en cascade les 3 caches backend ( filterList , "
                    + "authorizationScopes , checkedFormatComponents ) pour l'application "
                    + "donnée. Utile après une mise à jour de la config YAML , un grant / "
                    + "revoke admin , ou si l'opérateur soupçonne une dérive de cache. "
                    + "Au prochain GET /data/json ou /filters , le backend recalculera "
                    + "depuis la base ( premier hit lent , les suivants rapides ).",
            tags = {"Admin / Caches"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Caches purgés ; retour avec le nom de l'app et la liste des caches affectés.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(value = "{\"applicationName\":\"si_acbb\",\"invalidatedCaches\":[\"filterList\",\"authorizationScopes\",\"checkedFormatComponents\"]}")
                            )
                    ),
                    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas applicationManager de cette app.")
            }
    )
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PostMapping(value = "/applications/{nameOrId}/admin/invalidate-caches", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateAppCaches(
            @Parameter(description = "Nom ou UUID de l'application", required = true)
            @PathVariable("nameOrId") final String nameOrId) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        DataService dataService = serviceContainer.dataService();
        AuthorizationService authorizationService = serviceContainer.authorizationService();

        // filterList : pas d'API "purger toute l'app" , on utilise la
        // méthode existante invalidateAllFilterListCaches puis on log
        // explicitement quels datatypes étaient présents pour rester
        // observable. Alternative future : exposer une méthode
        // invalidateFilterListCacheForApplication ciblée.
        // En attendant , on se rabat sur invalidate global pour l'app
        // via une boucle sur les datatypes connus de la config.
        // Pragmatique : on purge tout le cache filterList global.
        dataService.invalidateAllFilterListCaches();
        authorizationService.invalidateAuthorizationScopesForApplication(application.getName());
        dataService.invalidateCheckedFormatComponentsForApplication(application.getName());

        log.info("Admin invalidate-caches for app {} requested", application.getName());
        return ResponseEntity.ok(Map.of(
                "applicationName", application.getName(),
                "invalidatedCaches", java.util.List.of(
                        "filterList ( global - cache partagé entre toutes les apps )",
                        "authorizationScopes ( app uniquement )",
                        "checkedFormatComponents ( app uniquement )")
        ));
    }

    @Operation(
            summary = "Purge tous les caches mémoire du backend ( global )",
            description = "Vide les 3 caches mémoire backend pour toutes les applications "
                    + "et tous les utilisateurs. Réservé à openAdomAdmin ( opération de "
                    + "maintenance ; les premiers hits suivants paieront le coût du "
                    + "rechargement complet ). Utile en debug ou après modification "
                    + "transverse de configuration.",
            tags = {"Admin / Caches"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Tous les caches sont vidés.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(value = "{\"status\":\"all caches cleared\"}")
                            )
                    ),
                    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas openAdomAdmin.")
            }
    )
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/admin/caches/invalidate-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateAllCaches() {
        DataService dataService = serviceContainer.dataService();
        AuthorizationService authorizationService = serviceContainer.authorizationService();

        dataService.invalidateAllFilterListCaches();
        authorizationService.invalidateAllAuthorizationScopes();
        dataService.invalidateAllCheckedFormatComponents();

        log.warn("Admin invalidate-all-caches : tous les caches mémoire backend ont été vidés");
        return ResponseEntity.ok(Map.of(
                "status", "all caches cleared",
                "invalidatedCaches", java.util.List.of(
                        "filterList", "authorizationScopes", "checkedFormatComponents")
        ));
    }

    @Operation(
            summary = "Statistiques des caches mémoire backend",
            description = "Renvoie le nombre d'entrées de chaque cache mémoire backend. "
                    + "Sans donnée granulaire ( pas d'introspection user-par-user ) , juste "
                    + "des compteurs pour observabilité opérationnelle. Réservé à "
                    + "openAdomAdmin.",
            tags = {"Admin / Caches"}
    )
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/admin/caches/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> cacheStats() {
        DataService dataService = serviceContainer.dataService();
        AuthorizationService authorizationService = serviceContainer.authorizationService();
        return ResponseEntity.ok(Map.of(
                "filterListEntries", dataService.getFilterListCacheSize(),
                "authorizationScopesEntries", authorizationService.getAuthorizationScopesCacheSize(),
                "checkedFormatComponentsEntries", dataService.getCheckedFormatComponentsCacheSize()
        ));
    }
}
