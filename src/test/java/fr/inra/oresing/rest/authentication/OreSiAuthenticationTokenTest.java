package fr.inra.oresing.rest.authentication;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.security.AuthorizationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("core.auth")
@DisplayName("Tests unitaires de OreSiAuthenticationToken")
class OreSiAuthenticationTokenTest {

    // ------------------------------------------------------------------ //
    //  Helpers                                                            //
    // ------------------------------------------------------------------ //

    private static OreSiAuthenticationToken tokenWithPath(Object principal, String path) {
        return new OreSiAuthenticationToken(principal, path, List.of(AuthorizationFilter.ROLE_AUTHENTIFIED_USER));
    }

    private static OreSiAuthenticationToken tokenWithPathAndRole(Object principal, String path,
                                                                   org.springframework.security.core.GrantedAuthority role) {
        return new OreSiAuthenticationToken(principal, path, List.of(role));
    }

    // ------------------------------------------------------------------ //
    //  isAuthenticated                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isAuthenticated")
    class IsAuthenticated {

        @Test
        @DisplayName("renvoie toujours true")
        void alwaysTrue() {
            OreSiAuthenticationToken token = new OreSiAuthenticationToken(null, null, Collections.emptyList());
            assertTrue(token.isAuthenticated());
        }
    }

    // ------------------------------------------------------------------ //
    //  isLogin                                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isLogin")
    class IsLogin {

        @Test
        @DisplayName("renvoie true quand le chemin se termine par /login")
        void trueWhenPathEndsWithLogin() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/api/v1/login");
            assertTrue(token.isLogin());
        }

        @Test
        @DisplayName("renvoie false quand le chemin ne se termine pas par /login")
        void falseWhenPathNotEndingWithLogin() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/api/v1/data");
            assertFalse(token.isLogin());
        }

        @Test
        @DisplayName("renvoie false quand credentials est null")
        void falseWhenCredentialsNull() {
            OreSiAuthenticationToken token = new OreSiAuthenticationToken(null, null, Collections.emptyList());
            assertFalse(token.isLogin());
        }
    }

    // ------------------------------------------------------------------ //
    //  isCreate                                                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isCreate")
    class IsCreate {

        @Test
        @DisplayName("renvoie true quand chemin se termine par /users et rôle UNAUTHENTIFIED_CREATE_USER")
        void trueWhenUsersPathAndCreateRole() {
            OreSiAuthenticationToken token = tokenWithPathAndRole(
                    null, "/api/v1/users", AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER);
            assertTrue(token.isCreate());
        }

        @Test
        @DisplayName("renvoie false si chemin est /users mais rôle incorrect")
        void falseWhenUsersPathWithWrongRole() {
            OreSiAuthenticationToken token = tokenWithPathAndRole(
                    null, "/api/v1/users", AuthorizationFilter.ROLE_AUTHENTIFIED_USER);
            assertFalse(token.isCreate());
        }

        @Test
        @DisplayName("renvoie false si chemin ne se termine pas par /users")
        void falseWhenNotUsersPath() {
            OreSiAuthenticationToken token = tokenWithPathAndRole(
                    null, "/api/v1/login", AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER);
            assertFalse(token.isCreate());
        }
    }

    // ------------------------------------------------------------------ //
    //  isUpdate                                                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("isUpdate")
    class IsUpdate {

        @Test
        @DisplayName("renvoie true quand chemin se termine par /users et rôle UNAUTHENTIFIED_UPDATE_USER")
        void trueWhenUsersPathAndUpdateRole() {
            OreSiAuthenticationToken token = tokenWithPathAndRole(
                    null, "/api/v1/users", AuthorizationFilter.ROLE_UNAUTHENTIFIED_UPDATE_USER);
            assertTrue(token.isUpdate());
        }

        @Test
        @DisplayName("renvoie false si rôle incorrect")
        void falseWhenWrongRole() {
            OreSiAuthenticationToken token = tokenWithPathAndRole(
                    null, "/api/v1/users", AuthorizationFilter.ROLE_AUTHENTIFIED_USER);
            assertFalse(token.isUpdate());
        }
    }

    // ------------------------------------------------------------------ //
    //  getRequestClient (méthode statique)                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getRequestClient")
    class GetRequestClient {

        @Test
        @DisplayName("retourne le client quand le principal est déjà OreSiUserRequestClient")
        void returnsRequestClientWhenPrincipalIsRequestClient() {
            OreSiUserRequestClient client = new OreSiUserRequestClient(UUID.randomUUID(), new OreSiUserRole());
            OreSiAuthenticationToken token = tokenWithPath(client, "/api/v1/data");
            assertEquals(client, OreSiAuthenticationToken.getRequestClient(token));
        }

        @Test
        @DisplayName("retourne un client avec l'id pour NotConnectedAuthentifiedActiveUser")
        void returnsClientForActiveUser() {
            OreSiUser user = new OreSiUser();
            user.setId(UUID.randomUUID());
            user.setLogin("testUser");
            NotConnectedAuthentifiedActiveUser activeUser = new NotConnectedAuthentifiedActiveUser(user, null);
            OreSiAuthenticationToken token = tokenWithPath(activeUser, "/api/v1/users");
            OreSiUserRequestClient client = OreSiAuthenticationToken.getRequestClient(token);
            assertThat(client).isNotNull();
            assertThat(client.id()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("retourne un client avec l'id pour NotConnectedAuthentifiedIdleUser")
        void returnsClientForIdleUser() {
            OreSiUser user = new OreSiUser();
            user.setId(UUID.randomUUID());
            user.setLogin("idleUser");
            NotConnectedAuthentifiedIdleUser idleUser = new NotConnectedAuthentifiedIdleUser(user, null);
            OreSiAuthenticationToken token = tokenWithPath(idleUser, "/api/v1/users");
            OreSiUserRequestClient client = OreSiAuthenticationToken.getRequestClient(token);
            assertThat(client).isNotNull();
            assertThat(client.id()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("retourne un client avec l'id pour NotConnectedAuthentifiedPendingUser")
        void returnsClientForPendingUser() {
            OreSiUser user = new OreSiUser();
            user.setId(UUID.randomUUID());
            user.setLogin("pendingUser");
            NotConnectedAuthentifiedPendingUser pendingUser = new NotConnectedAuthentifiedPendingUser(user);
            OreSiAuthenticationToken token = tokenWithPath(pendingUser, "/api/v1/users");
            OreSiUserRequestClient client = OreSiAuthenticationToken.getRequestClient(token);
            assertThat(client).isNotNull();
            assertThat(client.id()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("retourne un client avec l'id pour NotConnectedAuthentifiedMissingPasswordUser")
        void returnsClientForMissingPasswordUser() {
            OreSiUser user = new OreSiUser();
            user.setId(UUID.randomUUID());
            user.setLogin("missingPwdUser");
            NotConnectedAuthentifiedMissingPasswordUser missingPwdUser =
                    new NotConnectedAuthentifiedMissingPasswordUser(user, null);
            OreSiAuthenticationToken token = tokenWithPath(missingPwdUser, "/api/v1/users");
            OreSiUserRequestClient client = OreSiAuthenticationToken.getRequestClient(token);
            assertThat(client).isNotNull();
            assertThat(client.id()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("retourne null pour NotConnectedUnauthentifiedUserForCreate")
        void returnsNullForUnauthentifiedCreateUser() {
            NotConnectedUnauthentifiedUserForCreate createUser = new NotConnectedUnauthentifiedUserForCreate();
            OreSiAuthenticationToken token = tokenWithPath(createUser, "/api/v1/users");
            assertNull(OreSiAuthenticationToken.getRequestClient(token));
        }

        @Test
        @DisplayName("retourne null pour un principal inconnu")
        void returnsNullForUnknownPrincipal() {
            OreSiAuthenticationToken token = tokenWithPath("unknownPrincipal", "/api/v1/data");
            assertNull(OreSiAuthenticationToken.getRequestClient(token));
        }
    }

    // ------------------------------------------------------------------ //
    //  getLoginAdminResult                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getLoginAdminResult")
    class GetLoginAdminResult {

        @Test
        @DisplayName("retourne le LoginAdminResult si le principal est bien de ce type")
        void returnsLoginAdminResult() {
            LoginAdminResult result = mock(LoginAdminResult.class);
            OreSiAuthenticationToken token = tokenWithPath(result, "/api/v1/login");
            assertEquals(result, token.getLoginAdminResult());
        }

        @Test
        @DisplayName("retourne null si le principal n'est pas un LoginAdminResult")
        void returnsNullWhenNotLoginAdminResult() {
            OreSiAuthenticationToken token = tokenWithPath(
                    new OreSiUserRequestClient(UUID.randomUUID(), new OreSiUserRole()), "/api/v1/data");
            assertNull(token.getLoginAdminResult());
        }

        @Test
        @DisplayName("retourne null si le principal est null")
        void returnsNullWhenPrincipalNull() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/api/v1/data");
            assertNull(token.getLoginAdminResult());
        }
    }

    // ------------------------------------------------------------------ //
    //  getNotConnectedUser                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getNotConnectedUser")
    class GetNotConnectedUser {

        @Test
        @DisplayName("retourne le NotConnectedUser si le principal l'implémente")
        void returnsNotConnectedUser() {
            NotConnectedUnauthentifiedUserForCreate createUser = new NotConnectedUnauthentifiedUserForCreate();
            OreSiAuthenticationToken token = tokenWithPath(createUser, "/api/v1/users");
            assertEquals(createUser, token.getNotConnectedUser());
        }

        @Test
        @DisplayName("retourne null si le principal n'implémente pas NotConnectedUser")
        void returnsNullWhenNotNotConnectedUser() {
            OreSiAuthenticationToken token = tokenWithPath(
                    new OreSiUserRequestClient(UUID.randomUUID(), new OreSiUserRole()), "/api/v1/data");
            assertNull(token.getNotConnectedUser());
        }
    }

    // ------------------------------------------------------------------ //
    //  getBearerJwt                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getBearerJwt")
    class GetBearerJwt {

        @Test
        @DisplayName("retourne 'Bearer <token>' après setJwtToken")
        void returnsBearerFormattedJwt() {
            OreSiAuthenticationToken token = tokenWithPath(null, "");
            token.setJwtToken("eyJhbGciOiJIUzI1NiJ9.test.sig");
            assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.test.sig", token.getBearerJwt());
        }
    }

    // ------------------------------------------------------------------ //
    //  getPrincipal / getCredentials                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getPrincipal et getCredentials")
    class PrincipalAndCredentials {

        @Test
        @DisplayName("getPrincipal retourne le principal passé au constructeur")
        void getPrincipalReturnsPrincipal() {
            OreSiUserRequestClient client = new OreSiUserRequestClient(UUID.randomUUID(), new OreSiUserRole());
            OreSiAuthenticationToken token = tokenWithPath(client, "/path");
            assertEquals(client, token.getPrincipal());
        }

        @Test
        @DisplayName("getCredentials retourne le chemin passé comme credentials")
        void getCredentialsReturnsPath() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/api/v1/login");
            assertEquals("/api/v1/login", token.getCredentials());
        }
    }

    // ------------------------------------------------------------------ //
    //  setters (applicationName, dataName, systemPersona, etc.)          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Setters de contexte")
    class ContextSetters {

        @Test
        @DisplayName("setApplicationName / getApplicationName")
        void applicationName() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/");
            token.setApplicationName("myApp");
            assertEquals("myApp", token.getApplicationName());
        }

        @Test
        @DisplayName("setDataName / getDataName")
        void dataName() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/");
            token.setDataName("myData");
            assertEquals("myData", token.getDataName());
        }

        @Test
        @DisplayName("setSystemPersona / getSystemPersona")
        void systemPersona() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/");
            SystemPersona persona = mock(OpenAdomAdmin.class);
            token.setSystemPersona(persona);
            assertEquals(persona, token.getSystemPersona());
        }

        @Test
        @DisplayName("setApplicationPersonna / getApplicationPersona")
        void applicationPersona() {
            OreSiAuthenticationToken token = tokenWithPath(null, "/");
            ApplicationPersona persona = mock(ApplicationAdminUser.class);
            token.setApplicationPersonna(persona);
            assertEquals(persona, token.getApplicationPersona());
        }
    }
}