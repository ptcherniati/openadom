package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.AuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires sur {@link CacheAdminResources}.
 *
 * <p>Au lieu d'un test d'intégration {@code @SpringBootTest} ( lourd , exige
 * la chaîne d'auth complète ) , on injecte des mocks Mockito sur le
 * {@link ServiceContainer} pour vérifier que les endpoints :
 * <ul>
 *   <li>cascadent les bonnes invalidations ( filterList + scopes +
 *       checkedFormatComponents pour invalidate-app , global pour
 *       invalidate-all ) ;
 *   <li>renvoient bien le shape de réponse attendu ( clés , types ) ;
 *   <li>routent la cible app vers les méthodes ciblées ( pas la purge
 *       globale - garde-fou anti privilege escalation ).
 * </ul>
 *
 * <p>La couche {@code @PreAuthorize} est testée séparément via
 * {@code AuthorizationResourcesTest} ( tests d'intégration full Spring ).
 */
class CacheAdminResourcesUnitTest {

    private ServiceContainer serviceContainer;
    private DataService dataService;
    private AuthorizationService authorizationService;
    private ApplicationService applicationService;
    private fr.inra.oresing.cache.DataVersioningScopeCacheService dataVersioningScopeCacheService;
    private fr.inra.oresing.cache.CacheSizeEstimator cacheSizeEstimator;
    private fr.inra.oresing.cache.CachePreloader cachePreloader;
    private CacheAdminResources resources;

    @BeforeEach
    void setUp() {
        serviceContainer = mock(ServiceContainer.class);
        dataService = mock(DataService.class);
        authorizationService = mock(AuthorizationService.class);
        applicationService = mock(ApplicationService.class);
        dataVersioningScopeCacheService = mock(fr.inra.oresing.cache.DataVersioningScopeCacheService.class);
        cacheSizeEstimator = mock(fr.inra.oresing.cache.CacheSizeEstimator.class);
        cachePreloader = mock(fr.inra.oresing.cache.CachePreloader.class);

        when(serviceContainer.dataService()).thenReturn(dataService);
        when(serviceContainer.authorizationService()).thenReturn(authorizationService);
        when(serviceContainer.applicationService()).thenReturn(applicationService);
        when(serviceContainer.dataVersioningScopeCacheService()).thenReturn(dataVersioningScopeCacheService);

        resources = new CacheAdminResources(serviceContainer, cacheSizeEstimator, cachePreloader);
    }

    @Test
    void invalidateAppCaches_purgeLes3CachesParAppName() {
        Application app = mock(Application.class);
        when(app.getName()).thenReturn("si_acbb");
        when(applicationService.getApplication("si_acbb")).thenReturn(app);

        ResponseEntity<Map<String, Object>> response = resources.invalidateAppCaches("si_acbb");

        // Garde-fou anti privilege escalation : on cible par appName ,
        // jamais le cache global filterList ( qui purgerait toutes les apps ).
        verify(dataService).invalidateFilterListCacheForApplication("si_acbb");
        verify(dataService, never()).invalidateAllFilterListCaches();
        verify(authorizationService).invalidateAuthorizationScopesForApplication("si_acbb");
        verify(dataService).invalidateCheckedFormatComponentsForApplication("si_acbb");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("si_acbb", body.get("applicationName"));
        assertNotNull(body.get("invalidatedCaches"));
    }

    @Test
    void invalidateAllCaches_purgeLes3CachesGlobalement() {
        ResponseEntity<Map<String, Object>> response = resources.invalidateAllCaches();

        verify(dataService).invalidateAllFilterListCaches();
        verify(authorizationService).invalidateAllAuthorizationScopes();
        verify(dataService).invalidateAllCheckedFormatComponents();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("all caches cleared", body.get("status"));
    }

    @Test
    void cacheStats_renvoieFlagCapTtlEntriesParCache() {
        when(dataService.isFilterListCacheEnabled()).thenReturn(true);
        when(dataService.getFilterListCacheMaxEntries()).thenReturn(50);
        when(dataService.getFilterListCacheSize()).thenReturn(12);

        when(authorizationService.isAuthorizationScopesCacheEnabled()).thenReturn(true);
        when(authorizationService.getAuthorizationScopesCacheMaxEntries()).thenReturn(200);
        when(authorizationService.getAuthorizationScopesCacheTtlMinutes()).thenReturn(120L);
        when(authorizationService.getAuthorizationScopesCacheSize()).thenReturn(47);

        when(dataService.isCheckedFormatComponentsCacheEnabled()).thenReturn(false);
        when(dataService.getCheckedFormatComponentsCacheMaxEntries()).thenReturn(200);
        when(dataService.getCheckedFormatComponentsCacheTtlMinutes()).thenReturn(0L);
        when(dataService.getCheckedFormatComponentsCacheSize()).thenReturn(0);

        when(dataService.getFrontEtagCacheMaxEntries()).thenReturn(50);
        when(dataService.getFrontEtagCacheMaxBytesMb()).thenReturn(20);

        ResponseEntity<Map<String, Object>> response = resources.cacheStats();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> filterList = (Map<String, Object>) body.get("filterList");
        assertEquals(true, filterList.get("enabled"));
        assertEquals(50, filterList.get("maxEntries"));
        // ttlMinutes harmonise en long depuis le refacto cacheEntryStats
        assertEquals(0L, filterList.get("ttlMinutes"));
        assertEquals(12, filterList.get("entries"));

        @SuppressWarnings("unchecked")
        Map<String, Object> scopes = (Map<String, Object>) body.get("authorizationScopes");
        assertEquals(120L, scopes.get("ttlMinutes"));
        assertEquals(47, scopes.get("entries"));

        @SuppressWarnings("unchecked")
        Map<String, Object> checkedFormat = (Map<String, Object>) body.get("checkedFormatComponents");
        assertEquals(false, checkedFormat.get("enabled"));
        assertEquals(0, checkedFormat.get("entries"));

        @SuppressWarnings("unchecked")
        Map<String, Object> frontDefaults = (Map<String, Object>) body.get("frontEtagDefaults");
        assertEquals(50, frontDefaults.get("maxEntries"));
        assertEquals(20, frontDefaults.get("maxBytesMb"));
    }

    @Test
    void invalidateAppCaches_appliedExactNameFromService() {
        Application app = mock(Application.class);
        when(app.getName()).thenReturn("resolved_name");
        when(applicationService.getApplication(eq("alias_or_uuid"))).thenReturn(app);

        ResponseEntity<Map<String, Object>> response = resources.invalidateAppCaches("alias_or_uuid");

        // Le nom utilisé pour invalider est celui résolu par
        // ApplicationService.getApplication , pas le path-param brut
        // ( supporte alias / UUID ).
        verify(dataService).invalidateFilterListCacheForApplication("resolved_name");
        verify(authorizationService).invalidateAuthorizationScopesForApplication("resolved_name");
        verify(dataService).invalidateCheckedFormatComponentsForApplication("resolved_name");

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("resolved_name", body.get("applicationName"));
        assertTrue(body.get("invalidatedCaches") instanceof java.util.List<?>);
    }
}
