package fr.inra.oresing.rest.security;

import fr.inra.oresing.monitoring.session.JwtBlacklistRegistry;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.exceptions.OreExceptionHandler;
import fr.inra.oresing.rest.model.authorization.CurrentUserRolesResult;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires de la logique de persistance du SecurityContext
 * dans les attributs de requête (fix #62 pour le dispatch ASYNC Flux<>).
 */
@Tag("core.auth")
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationFilter — saveSecurityContextToRequest (fix #62 async dispatch)")
class AuthorizationFilterSecurityContextTest {

    /** Nom de l'attribut de requête utilisé par RequestAttributeSecurityContextRepository. */
    private static final String SECURITY_CONTEXT_ATTR =
            RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME;

    @Mock
    private ServiceContainer serviceContainer;
    @Mock
    private JWTExtractor jwtExtractor;
    @Mock
    private OreExceptionHandler exceptionHandler;
    @Mock
    private JwtBlacklistRegistry jwtBlacklist;

    private JsonRowMapper<?> realMapper;
    private AuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        realMapper = new JsonRowMapper<>();
        filter = new AuthorizationFilter(serviceContainer, realMapper, jwtExtractor, exceptionHandler, jwtBlacklist);
        // Nettoyer le SecurityContextHolder entre les tests
        SecurityContextHolder.clearContext();
        // Nettoyer le thread-local OreSiApiRequestContext
        OreSiApiRequestContext.setAuthenticationToken(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        OreSiApiRequestContext.setAuthenticationToken(null);
    }

    // ------------------------------------------------------------------ //
    //  saveSecurityContextToRequest via doFilter — token déjà présent    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Quand authenticationToken est déjà présent (1ère branche doFilter)")
    class WhenTokenAlreadyPresent {

        @Test
        @DisplayName("saveSecurityContextToRequest sauvegarde le contexte en attribut de requête")
        void savesSecurityContextAttributeWhenAuthPresent() throws IOException, ServletException {
            // Préparer : créer un token et le placer dans le SecurityContextHolder
            OreSiAuthenticationToken token = buildDummyToken();
            OreSiApiRequestContext.setAuthenticationToken(token);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = (req, resp) -> { /* no-op */ };

            filter.doFilter(request, response, chain);

            // L'attribut de sécurité doit avoir été posé sur la requête
            assertThat(request.getAttribute(SECURITY_CONTEXT_ATTR))
                    .as("RequestAttributeSecurityContextRepository doit avoir sauvé le contexte")
                    .isNotNull();
        }

        @Test
        @DisplayName("le filtre passe directement à chain.doFilter après save (retour précoce)")
        void callsChainAfterSave() throws IOException, ServletException {
            OreSiAuthenticationToken token = buildDummyToken();
            OreSiApiRequestContext.setAuthenticationToken(token);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    // ------------------------------------------------------------------ //
    //  saveSecurityContextToRequest — contexte sans authentication       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Quand le SecurityContext n'a pas d'authentication")
    class WhenNoAuthentication {

        @Test
        @DisplayName("saveSecurityContextToRequest ne pose pas d'attribut si authentication est null")
        void doesNotSaveWhenAuthenticationIsNull() throws Exception {
            // SecurityContextHolder vide → authentication null
            SecurityContextHolder.clearContext();
            // OreSiApiRequestContext vide → getAuthenticationToken() = null
            OreSiApiRequestContext.setAuthenticationToken(null);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            // On ne peut pas atteindre saveSecurityContextToRequest directement via doFilter
            // si le token OreSi est null (le filtre continue son chemin normal).
            // On utilise la réflexion pour appeler la méthode privée directement.
            callPrivateSaveSecurityContextToRequest(filter, request, response);

            assertThat(request.getAttribute(SECURITY_CONTEXT_ATTR))
                    .as("Sans authentication, rien ne doit être sauvé dans l'attribut de requête")
                    .isNull();
        }

        @Test
        @DisplayName("saveSecurityContextToRequest ne pose pas d'attribut si le contexte lui-même est null")
        void doesNotSaveWhenContextIsNull() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            SecurityContextHolder.clearContext();

            callPrivateSaveSecurityContextToRequest(filter, request, response);

            assertThat(request.getAttribute(SECURITY_CONTEXT_ATTR)).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  saveSecurityContextToRequest — contexte AVEC authentication       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Quand le SecurityContext a une authentication non-null")
    class WhenAuthenticationPresent {

        @Test
        @DisplayName("saveSecurityContextToRequest pose l'attribut dans la requête")
        void savesContextWhenAuthPresent() throws Exception {
            OreSiAuthenticationToken token = buildDummyToken();
            OreSiApiRequestContext.setAuthenticationToken(token);

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            callPrivateSaveSecurityContextToRequest(filter, request, response);

            assertThat(request.getAttribute(SECURITY_CONTEXT_ATTR))
                    .as("SecurityContext doit être sauvé en attribut de requête")
                    .isNotNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private static OreSiAuthenticationToken buildDummyToken() {
        CurrentUserRolesResult roles = new CurrentUserRolesResult(
                Collections.emptyMap(), UUID.randomUUID(), "test-user",
                false, false, Collections.emptyList(), false);
        LoginAdminResult loginAdminResult = new LoginAdminResult(
                UUID.randomUUID(), "test-user", "test@example.com", "active",
                roles, Collections.emptySet(), Collections.emptyMap());
        return new OreSiAuthenticationToken(
                loginAdminResult,
                "/api/v1/test",
                Collections.singletonList(AuthorizationFilter.ROLE_AUTHENTIFIED_USER)
        );
    }

    /**
     * Appelle la méthode privée {@code saveSecurityContextToRequest} via réflexion.
     */
    private static void callPrivateSaveSecurityContextToRequest(
            AuthorizationFilter filter,
            MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        var method = AuthorizationFilter.class.getDeclaredMethod(
                "saveSecurityContextToRequest",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        method.setAccessible(true);
        method.invoke(filter, request, response);
    }
}