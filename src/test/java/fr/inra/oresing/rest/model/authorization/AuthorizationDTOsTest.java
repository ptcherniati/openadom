package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires des DTOs d'autorisation.
 * Aucun contexte Spring.
 */
@DisplayName("Authorization DTOs")
@Tag("domain.model")
class AuthorizationDTOsTest {

    // ---------------------------------------------------------
    // AuthorizationsForUserResult.Roles enum
    // ---------------------------------------------------------

    @Nested
    @DisplayName("AuthorizationsForUserResult.Roles enum")
    class RolesEnumTest {

        @Test
        void allValues() {
            assertThat(AuthorizationsForUserResult.Roles.values())
                    .containsExactlyInAnyOrder(
                            AuthorizationsForUserResult.Roles.UPLOAD,
                            AuthorizationsForUserResult.Roles.DOWNLOAD,
                            AuthorizationsForUserResult.Roles.READ,
                            AuthorizationsForUserResult.Roles.PUBLICATION,
                            AuthorizationsForUserResult.Roles.ANY,
                            AuthorizationsForUserResult.Roles.APPLICATION_USER,
                            AuthorizationsForUserResult.Roles.ACTIVE_APPLICATION_USER,
                            AuthorizationsForUserResult.Roles.DELETE);
        }

        @Test
        void valueOf() {
            assertThat(AuthorizationsForUserResult.Roles.valueOf("UPLOAD"))
                    .isEqualTo(AuthorizationsForUserResult.Roles.UPLOAD);
        }
    }

    // ---------------------------------------------------------
    // CreateUserResult record
    // ---------------------------------------------------------

    @Nested
    @DisplayName("CreateUserResult record")
    class CreateUserResultTest {

        @Test
        void constructor() {
            UUID id = UUID.randomUUID();
            CreateUserResult r = new CreateUserResult(id);
            assertThat(r.userId()).isEqualTo(id);
        }
    }

    // ---------------------------------------------------------
    // AdditionalFileAuthorizationRequest record
    // ---------------------------------------------------------

    @Nested
    @DisplayName("AdditionalFileAuthorizationRequest record")
    class AdditionalFileAuthorizationRequestTest {

        @Test
        void nonNullAuthorizationsAreCopiedAsImmutable() {
            Map<String, AuthorizationInput> auths = Map.of("key", new AuthorizationInput());
            AdditionalFileAuthorizationRequest req =
                    new AdditionalFileAuthorizationRequest(auths);
            assertThat(req.authorizations()).isNotNull().containsKey("key");
        }

        @Test
        void nullAuthorizationsStayNull() {
            AdditionalFileAuthorizationRequest req =
                    new AdditionalFileAuthorizationRequest(null);
            assertThat(req.authorizations()).isNull();
        }
    }

    // ---------------------------------------------------------
    // UserAuthorizationForApplication record
    // ---------------------------------------------------------

    @Nested
    @DisplayName("UserAuthorizationForApplication record")
    class UserAuthorizationForApplicationTest {

        @Test
        void constructor() {
            UUID id = UUID.randomUUID();
            UserAuthorizationForApplication r = new UserAuthorizationForApplication(
                    "myApp", id, "alice", "alice@x.com", "active",
                    true, false, Set.of("auth1"), true, true);

            assertThat(r.applicationName()).isEqualTo("myApp");
            assertThat(r.id()).isEqualTo(id);
            assertThat(r.login()).isEqualTo("alice");
            assertThat(r.applicationManager()).isTrue();
            assertThat(r.userManager()).isFalse();
            assertThat(r.isValidCharte()).isTrue();
            assertThat(r.isApplicationUser()).isTrue();
        }
    }

    // ---------------------------------------------------------
    // AuthorizationRequestError
    // ---------------------------------------------------------

    @Nested
    @DisplayName("AuthorizationRequestError")
    class AuthorizationRequestErrorTest {

        @Test
        void constructorWithEnum() {
            AuthorizationRequestError err = new AuthorizationRequestError(
                    AuthorizationRequestException.NO_AUTHORIZATION_NAME,
                    Map.of("k", "v"));
            assertThat(err.getMessage())
                    .isEqualTo(AuthorizationRequestException.NO_AUTHORIZATION_NAME.getMessage());
        }

        @Test
        void constructorWithString() {
            AuthorizationRequestError err = new AuthorizationRequestError("direct msg");
            assertThat(err.getMessage()).isEqualTo("direct msg");
        }

        @Test
        void isThrowable() {
            assertThatThrownBy(() -> { throw new AuthorizationRequestError("oops"); })
                    .isInstanceOf(AuthorizationRequestError.class)
                    .hasMessage("oops");
        }
    }

    // ---------------------------------------------------------
    // AuthorizationsReferencesResult record
    // ---------------------------------------------------------

    @Nested
    @DisplayName("AuthorizationsReferencesResult record")
    class AuthorizationsReferencesResultTest {

        @Test
        void constructor() {
            AuthorizationsReferencesResult r = new AuthorizationsReferencesResult(
                    Map.of(), "appName", true);
            assertThat(r.applicationName()).isEqualTo("appName");
            assertThat(r.isAdministrator()).isTrue();
            assertThat(r.authorizationResults()).isEmpty();
        }
    }

    // ---------------------------------------------------------
    // LoginAdminResult record – constructeur de convenance
    // ---------------------------------------------------------

    @Nested
    @DisplayName("LoginAdminResult")
    class LoginAdminResultTest {

        @Test
        void convenienceConstructor() {
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            CurrentUserRolesResult roles = new CurrentUserRolesResult(
                    Map.of(), userId, "admin", true, true, List.of(), false);
            Timestamp ts = Timestamp.from(Instant.now());

            LoginAdminResult r = new LoginAdminResult(
                    id, "admin", "admin@x.com", "active",
                    roles, Set.of(), Map.of("app1", ts));

            assertThat(r.id()).isEqualTo(id);
            assertThat(r.login()).isEqualTo("admin");
            // Le constructeur de convenance calcule les flags depuis roles
            assertThat(r.authorizedForApplicationCreation()).isTrue();
            assertThat(r.openAdomAdmin()).isTrue();
        }
    }
}