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
import org.springframework.web.bind.annotation.*;

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

    private static final String KEY_TTL_MINUTES = "ttlMinutes";

    private final ServiceContainer serviceContainer;
    private final fr.inra.oresing.cache.CacheSizeEstimator cacheSizeEstimator;
    private final fr.inra.oresing.cache.CachePreloader cachePreloader;

    public CacheAdminResources(ServiceContainer serviceContainer,
                                fr.inra.oresing.cache.CacheSizeEstimator cacheSizeEstimator,
                                fr.inra.oresing.cache.CachePreloader cachePreloader) {
        this.serviceContainer = serviceContainer;
        this.cacheSizeEstimator = cacheSizeEstimator;
        this.cachePreloader = cachePreloader;
    }

    @Operation(summary = "Prechauffe les caches filterList / checkedFormatComponents "
            + "( et optionnellement referencedFiles ) pour une application . "
            + "Operation longue mais non destructive ; les 1eres requetes des "
            + "users finals beneficient ensuite des hits cache .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/applications/{nameOrId}/admin/preload-caches",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> preloadCaches(
            @PathVariable("nameOrId") String nameOrId,
            @org.springframework.web.bind.annotation.RequestParam(name = "includeReferencedFiles", required = false, defaultValue = "true") boolean includeReferencedFiles,
            @org.springframework.web.bind.annotation.RequestParam(name = "parallel", required = false, defaultValue = "true") boolean parallel) {
        fr.inra.oresing.domain.application.Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        fr.inra.oresing.cache.CachePreloader.PreloadReport report =
                cachePreloader.preload(application, includeReferencedFiles, parallel);
        // Invalide le size cache : les caches viennent d'etre remplis ,
        // la prochaine consultation des tailles doit recompute .
        cacheSizeEstimator.invalidate();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("applicationName", application.getName());
        body.put("filterListPreloaded", report.filterListPreloaded());
        body.put("checkedFormatPreloaded", report.checkedFormatPreloaded());
        body.put("referencedFilesPreloaded", report.referencedFilesPreloaded());
        body.put("errors", report.errors());
        body.put("durationMs", report.durationMs());
        body.put("parallel", report.parallel());
        body.put("parallelism", report.parallelism());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Tailles memoire approximatives des caches JVM .",
            description = "Serialise chaque entree via Jackson et somme les bytes . "
                    + "Resultat memoise selon openadom.cache.sizes.ttl-minutes ; passer "
                    + "force=true pour recompute immediat ( apres purge / preload ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/admin/caches/sizes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> cacheSizes(
            @org.springframework.web.bind.annotation.RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        fr.inra.oresing.cache.CacheSizeEstimator.SizeReport report = cacheSizeEstimator.getReport(force);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("bytesByCache", report.bytesByCache());
        body.put("computedAt", report.computedAt().toString());
        body.put("durationMs", report.durationMs());
        body.put(KEY_TTL_MINUTES, cacheSizeEstimator.ttlMinutes());
        body.put("totalBytes", report.bytesByCache().values().stream().mapToLong(Long::longValue).sum());
        return ResponseEntity.ok(body);
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
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY') "
            + "or hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE')")
    @PostMapping(value = "/applications/{nameOrId}/admin/invalidate-caches", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> invalidateAppCaches(
            @Parameter(description = "Nom ou UUID de l'application", required = true)
            @PathVariable("nameOrId") final String nameOrId) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        DataService dataService = serviceContainer.dataService();
        AuthorizationService authorizationService = serviceContainer.authorizationService();

        // Purge ciblée par application :
        //  - filterList : retire toutes les entrées dont la clé commence par
        //    appName::*  ( cf. invalidateFilterListCacheForApplication ).
        //  - authorizationScopes : pareil ( clé contient ::appName:: ) .
        //  - checkedFormatComponents : pareil ( clé commence par appName:: ).
        // Aucun effet sur les caches des autres apps : pas de privilege
        // escalation possible.
        dataService.invalidateFilterListCacheForApplication(application.getName());
        authorizationService.invalidateAuthorizationScopesForApplication(application.getName());
        dataService.invalidateCheckedFormatComponentsForApplication(application.getName());
        // Cache materialise V8 ( table dediee per-app ) : DELETE rows pour
        // cette application . Les triggers SQL et hooks Java continuent de
        // fonctionner ; cet appel est l'equivalent admin manuel .
        serviceContainer.dataVersioningScopeCacheService().invalidateAllForApp(application);

        log.info("Admin invalidate-caches for app {} requested", application.getName());
        return ResponseEntity.ok(Map.of(
                "applicationName", application.getName(),
                "invalidatedCaches", java.util.List.of(
                        "filterList ( app uniquement )",
                        "authorizationScopes ( app uniquement )",
                        "checkedFormatComponents ( app uniquement )",
                        "dataVersioningScope ( app uniquement )")
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
            summary = "Statistiques + configuration des caches mémoire backend",
            description = "Renvoie pour chaque cache mémoire backend : "
                    + "( a ) son flag d'activation , ( b ) sa capacité max ( max-entries ) , "
                    + "( c ) son TTL en minutes ( 0 = pas d'expiration auto , invalidation "
                    + "explicite uniquement ) , ( d ) le nombre d'entrées actuellement "
                    + "stockées. Réservé à openAdomAdmin. Utile pour vérifier qu'un tuning "
                    + "via env vars est bien appliqué et observer l'occupation runtime des "
                    + "caches sans devoir attacher de profiler à la JVM.",
            tags = {"Admin / Caches"},
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Stats + config par cache.",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(value = "{\n"
                                            + "  \"filterList\": {\"enabled\":true,\"maxEntries\":50,\"ttlMinutes\":0,\"entries\":12},\n"
                                            + "  \"authorizationScopes\": {\"enabled\":true,\"maxEntries\":200,\"ttlMinutes\":5,\"entries\":47},\n"
                                            + "  \"checkedFormatComponents\": {\"enabled\":true,\"maxEntries\":200,\"ttlMinutes\":5,\"entries\":8},\n"
                                            + "  \"frontEtagDefaults\": {\"maxEntries\":50,\"maxBytesMb\":20}\n"
                                            + "}")
                            )
                    ),
                    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas openAdomAdmin.")
            }
    )
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/admin/caches/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> cacheStats() {
        DataService dataService = serviceContainer.dataService();
        AuthorizationService authorizationService = serviceContainer.authorizationService();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("filterList", cacheEntryStats(
                dataService.isFilterListCacheEnabled(),
                dataService.getFilterListCacheMaxEntries(),
                0,
                dataService.getFilterListCacheSize(),
                dataService.getFilterListCacheLastWriteAt()));
        body.put("authorizationScopes", cacheEntryStats(
                authorizationService.isAuthorizationScopesCacheEnabled(),
                authorizationService.getAuthorizationScopesCacheMaxEntries(),
                authorizationService.getAuthorizationScopesCacheTtlMinutes(),
                authorizationService.getAuthorizationScopesCacheSize(),
                authorizationService.getAuthorizationScopesCacheLastWriteAt()));
        body.put("checkedFormatComponents", cacheEntryStats(
                dataService.isCheckedFormatComponentsCacheEnabled(),
                dataService.getCheckedFormatComponentsCacheMaxEntries(),
                dataService.getCheckedFormatComponentsCacheTtlMinutes(),
                dataService.getCheckedFormatComponentsCacheSize(),
                dataService.getCheckedFormatComponentsCacheLastWriteAt()));
        body.put("frontEtagDefaults", Map.of(
                "maxEntries", dataService.getFrontEtagCacheMaxEntries(),
                "maxBytesMb", dataService.getFrontEtagCacheMaxBytesMb()
        ));
        return ResponseEntity.ok(body);
    }

    /**
     * Builder homogene d'une entree stats avec le champ {@code lastUpdatedAt}
     * ( ISO instant ou null si jamais ecrit ) . Centralise le mapping pour
     * que tous les caches memoire exposent la meme forme dans la reponse
     * JSON ( DRY ) .
     */
    private static Map<String, Object> cacheEntryStats(boolean enabled, int maxEntries,
                                                       long ttlMinutes, int entries,
                                                       java.time.Instant lastWriteAt) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("maxEntries", maxEntries);
        m.put("ttlMinutes", ttlMinutes);
        m.put("entries", entries);
        m.put("lastUpdatedAt", lastWriteAt != null ? lastWriteAt.toString() : null);
        return m;
    }
}