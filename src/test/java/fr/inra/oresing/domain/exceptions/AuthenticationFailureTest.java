package fr.inra.oresing.domain.exceptions;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.domain.user.CreateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link AuthenticationFailure} – 3 constructeurs + getParams().
 */
@Tag("domain.model")
@DisplayName("AuthenticationFailure – constructeurs et params")
class AuthenticationFailureTest {

    // ─────────────────────────────────────────────────────────────────
    // Constructeur (String, CreateUserRequest)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("(message, CreateUserRequest) : params login+email présents")
    void constructorWithCreateUserRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setLogin("jdoe");
        req.setEmail("jdoe@example.com");
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.EXISTING_LOGIN, req);
        assertThat(f.getMessage()).isEqualTo(AuthenticationFailure.EXISTING_LOGIN);
        assertThat(f.getParams()).containsKeys(
                AuthenticationFailure.CONSTANT_LOGIN,
                AuthenticationFailure.CONSTANT_EMAIL);
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_LOGIN, "jdoe");
    }

    @Test
    @DisplayName("(message, CreateUserRequest null) : params vide")
    void constructorWithNullCreateUserRequest() {
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.EXISTING_LOGIN, (CreateUserRequest) null);
        assertThat(f.getParams()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // Constructeur (String, OreSiUser)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("(message, OreSiUser active) : params présents")
    void constructorWithOreSiUser() {
        OreSiUser user = new OreSiUser();
        user.setLogin("alice");
        user.setEmail("alice@example.com");
        user.setAccountstate(OreSiUser.OreSiUserStates.active);
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.INACTIVE_ACCOUNT, user);
        assertThat(f.getParams()).containsKeys(
                AuthenticationFailure.CONSTANT_LOGIN,
                AuthenticationFailure.CONSTANT_EMAIL,
                AuthenticationFailure.CONSTANT_ID);
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_LOGIN, "alice");
    }

    @Test
    @DisplayName("(message, OreSiUser null) : params vide")
    void constructorWithNullOreSiUser() {
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_PASSWORD, (OreSiUser) null);
        assertThat(f.getParams()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // Constructeur (String, LoginAdminResult)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("(message, LoginAdminResult) : params présents")
    void constructorWithLoginAdminResult() {
        UUID id = UUID.randomUUID();
        // Canonical constructor: id, login, email, state, authorizedForApplicationCreation,
        //                        openAdomAdmin, authorizations, chartes, currentUserRoles
        LoginAdminResult result = new LoginAdminResult(
                id, "bob", "bob@example.com",
                "active", false, false,
                java.util.Set.of("APP_ADMIN"),
                java.util.Map.of(),
                null);
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.BAD_VALIDATION_KEY, result);
        assertThat(f.getParams()).containsKeys(
                AuthenticationFailure.CONSTANT_LOGIN,
                AuthenticationFailure.CONSTANT_EMAIL,
                AuthenticationFailure.CONSTANT_ID,
                AuthenticationFailure.CONSTANT_STATE);
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_LOGIN, "bob");
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_ID, id.toString());
    }

    @Test
    @DisplayName("(message, LoginAdminResult null) : params vide")
    void constructorWithNullLoginAdminResult() {
        AuthenticationFailure f = new AuthenticationFailure(AuthenticationFailure.BAD_LOGIN_PASSWORD, (LoginAdminResult) null);
        assertThat(f.getParams()).isEmpty();
    }

    @Test
    @DisplayName("(message, LoginAdminResult avec champs null) : pas d'exception")
    void constructorWithLoginAdminResultNullFields() {
        // null fields: login, email, id, state, authorizations
        LoginAdminResult result = new LoginAdminResult(null, null, null, null, false, false, null, null, null);
        AuthenticationFailure f = new AuthenticationFailure("MSG", result);
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_LOGIN, "");
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_ID, "");
        assertThat(f.getParams()).containsEntry(AuthenticationFailure.CONSTANT_SUBMISSION_SCOPE, "");
    }
}
