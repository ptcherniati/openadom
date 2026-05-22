package fr.inra.oresing.rest.services;

import fr.inra.oresing.cache.MemoryCache;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de la logique de cache dans {@link AuthorizationService}.
 *
 * <p>Vérifie via mocks Mockito que :
 * <ul>
 *   <li>2 appels successifs à {@code getAuthorizationScopes} avec la même
 *       clé {@code (userId, app, menuType)} font 1 seul appel SQL ( 2e =
 *       hit cache ) ;
 *   <li>l'invalidation par appName retire toutes les entrées matching la
 *       clé , et le prochain appel re-tape la SQL ;
 *   <li>quand le flag est désactivé , chaque appel re-tape la SQL ( pas
 *       de cache ).
 * </ul>
 *
 * <p>On utilise la réflection pour injecter le {@link MemoryCache} et
 * les @Value normalement injectés par Spring , afin de garder le test
 * pur unit ( pas {@code @SpringBootTest} ).
 */
class AuthorizationServiceCacheTest {

    private AuthorizationService service;
    private OreSiRepository repository;
    private DataRepository dataRepository;
    private OreSiRepository.RepositoryForApplication repoForApp;
    private ServiceContainer serviceContainer;
    private AuthenticationService authenticationService;
    private OreSiUser currentUser;

    @BeforeEach
    void setUp() throws Exception {
        SqlService db = mock(SqlService.class);
        repository = mock(OreSiRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        serviceContainer = mock(ServiceContainer.class);
        authenticationService = mock(AuthenticationService.class);
        dataRepository = mock(DataRepository.class);
        repoForApp = mock(OreSiRepository.RepositoryForApplication.class);
        currentUser = mock(OreSiUser.class);

        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(currentUser.getId()).thenReturn(userId);
        when(authenticationService.getCurrentUser()).thenReturn(currentUser);
        when(serviceContainer.authenticationService()).thenReturn(authenticationService);
        when(repository.getRepository(any(Application.class))).thenReturn(repoForApp);
        when(repoForApp.data()).thenReturn(dataRepository);
        // Aucun node retourné -> arbre vide , sans impact sur le test.
        when(dataRepository.getNodesForMenu(any(MenuType.class))).thenReturn(List.of());

        service = new AuthorizationService(db, serviceContainer, repository, userRepository);

        // Inject @Value defaults via réflection pour bypasser Spring.
        setField(service, "authorizationScopesCacheEnabled", true);
        setField(service, "scopesCacheMaxEntries", 200);
        setField(service, "scopesCacheTtlMinutes", 0L);

        // Initialise le MemoryCache normalement créé par @PostConstruct.
        service.getClass().getDeclaredMethod("initScopesCache").setAccessible(true);
        var m = service.getClass().getDeclaredMethod("initScopesCache");
        m.setAccessible(true);
        m.invoke(service);
    }

    @Test
    void scopes_secondCallHitsCache_singleSqlCall() {
        Application app = appNamed("si_acbb");

        Map<String, List<GetGrantableResult.ReferenceScope>> r1 =
                service.getAuthorizationScopes(app, MenuType.submission);
        Map<String, List<GetGrantableResult.ReferenceScope>> r2 =
                service.getAuthorizationScopes(app, MenuType.submission);

        assertSame(r1, r2, "2e appel doit retourner la même instance ( cache hit )");
        verify(dataRepository, times(1)).getNodesForMenu(MenuType.submission);
    }

    @Test
    void scopes_distinctAppsHaveDistinctEntries() {
        Application appA = appNamed("appA");
        Application appB = appNamed("appB");

        service.getAuthorizationScopes(appA, MenuType.submission);
        service.getAuthorizationScopes(appB, MenuType.submission);
        service.getAuthorizationScopes(appA, MenuType.submission);
        service.getAuthorizationScopes(appB, MenuType.submission);

        // 1 SQL call per ( app , menuType ) couple , 2 totaux.
        verify(dataRepository, times(2)).getNodesForMenu(MenuType.submission);
        assertEquals(2, service.getAuthorizationScopesCacheSize());
    }

    @Test
    void invalidateForApplication_removesEntriesAndForcesSqlAgain() {
        Application app = appNamed("si_acbb");
        service.getAuthorizationScopes(app, MenuType.submission);
        service.getAuthorizationScopes(app, MenuType.authorization);
        assertEquals(2, service.getAuthorizationScopesCacheSize());

        service.invalidateAuthorizationScopesForApplication("si_acbb");

        assertEquals(0, service.getAuthorizationScopesCacheSize());
        // Re-call -> nouveau SQL miss
        service.getAuthorizationScopes(app, MenuType.submission);
        verify(dataRepository, times(3)).getNodesForMenu(any(MenuType.class));
    }

    @Test
    void invalidateForApplication_doesNotPurgeOtherApps() {
        service.getAuthorizationScopes(appNamed("appA"), MenuType.submission);
        service.getAuthorizationScopes(appNamed("appB"), MenuType.submission);

        service.invalidateAuthorizationScopesForApplication("appA");

        assertEquals(1, service.getAuthorizationScopesCacheSize(),
                "appB doit rester en cache après purge de appA");
    }

    @Test
    void cacheDisabled_alwaysHitsSql() throws Exception {
        setField(service, "authorizationScopesCacheEnabled", false);
        Application app = appNamed("si_acbb");

        service.getAuthorizationScopes(app, MenuType.submission);
        service.getAuthorizationScopes(app, MenuType.submission);
        service.getAuthorizationScopes(app, MenuType.submission);

        verify(dataRepository, times(3)).getNodesForMenu(MenuType.submission);
        assertEquals(0, service.getAuthorizationScopesCacheSize());
    }

    @Test
    void invalidateAll_clearsEntireCache() {
        service.getAuthorizationScopes(appNamed("appA"), MenuType.submission);
        service.getAuthorizationScopes(appNamed("appB"), MenuType.submission);

        service.invalidateAllAuthorizationScopes();

        assertEquals(0, service.getAuthorizationScopesCacheSize());
    }

    private Application appNamed(String name) {
        Application app = mock(Application.class);
        when(app.getName()).thenReturn(name);
        return app;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}