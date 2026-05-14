package fr.inra.oresing.domain.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link ApplicationUserResult} – of() variantes, getApplicationRoles.
 */
@Tag("domain.model")
@DisplayName("ApplicationUserResult – of() et getApplicationRoles()")
class ApplicationUserResultTest {

    private static final UUID APP_ID = UUID.randomUUID();

    private static OreSiUser activeUser(UUID appId, Timestamp charteTimestamp) {
        OreSiUser user = new OreSiUser();
        user.setId(UUID.randomUUID());
        user.setLogin("alice");
        user.setEmail("alice@example.com");
        user.setAccountstate(OreSiUser.OreSiUserStates.active);
        if (charteTimestamp != null) {
            user.getChartes().put(appId.toString(), charteTimestamp);
        }
        return user;
    }

    private static OreSiUser pendingUser() {
        OreSiUser user = new OreSiUser();
        user.setId(UUID.randomUUID());
        user.setLogin("bob");
        user.setEmail("bob@example.com");
        user.setAccountstate(OreSiUser.OreSiUserStates.pending);
        return user;
    }

    // ─────────────────────────────────────────────────────────────────
    // isApplicationManager : true quand un rôle se termine par APPLICATION_MANAGER
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isApplicationManager true si rôle APPLICATION_MANAGER contient userId")
    void isApplicationManagerTrue() {
        OreSiUser user = activeUser(APP_ID, Timestamp.from(Instant.now()));
        Map<String, List<String>> roles = new HashMap<>();
        roles.put("app_ADMIN_" + OreSiRightOnApplicationRole.APPLICATION_MANAGER,
                List.of(user.getId().toString()));
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, roles, null);
        assertThat(result.isApplicationManager()).isTrue();
        assertThat(result.isUserManager()).isTrue();
    }

    @Test
    @DisplayName("isApplicationManager false sans rôle APPLICATION_MANAGER")
    void isApplicationManagerFalse() {
        OreSiUser user = activeUser(APP_ID, Timestamp.from(Instant.now()));
        Map<String, List<String>> roles = new HashMap<>();
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, roles, null);
        assertThat(result.isApplicationManager()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────
    // isUserManager : true si APPLICATION_MANAGER OU USER_MANAGER
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isUserManager true via USER_MANAGER rôle seul")
    void isUserManagerViaUserManagerRole() {
        OreSiUser user = activeUser(APP_ID, Timestamp.from(Instant.now()));
        Map<String, List<String>> roles = new HashMap<>();
        roles.put("app_" + OreSiRightOnApplicationRole.USER_MANAGER,
                List.of(user.getId().toString()));
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, roles, null);
        assertThat(result.isUserManager()).isTrue();
        assertThat(result.isApplicationManager()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────
    // isApplicationUser : charte présente
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isApplicationUser true si charte présente et compte actif")
    void isApplicationUserWhenChartePresent() {
        Timestamp charteTs = Timestamp.from(Instant.now().minusSeconds(3600));
        OreSiUser user = activeUser(APP_ID, charteTs);
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, Map.of(), null);
        assertThat(result.isApplicationUser()).isTrue();
    }

    @Test
    @DisplayName("isApplicationUser false si compte pending (pas de charte) et pas de rôle")
    void isApplicationUserFalseForPending() {
        OreSiUser user = pendingUser();
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, Map.of(), null);
        assertThat(result.isApplicationUser()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────
    // isActiveApplicationUser
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isActiveApplicationUser true si charteTimestamp null (pas de date d'expiration)")
    void isActiveApplicationUserWhenNoExpiry() {
        Timestamp charteTs = Timestamp.from(Instant.now().minusSeconds(3600));
        OreSiUser user = activeUser(APP_ID, charteTs);
        // charteTimestamp null → toujours actif
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, Map.of(), null);
        assertThat(result.isActiveApplicationUser()).isTrue();
    }

    @Test
    @DisplayName("isActiveApplicationUser true si charte signée après la date de validité")
    void isActiveApplicationUserWhenCharteAfterExpiry() {
        Timestamp expiryTs = Timestamp.from(Instant.now().minusSeconds(7200)); // expiry 2h ago
        Timestamp charteTs = Timestamp.from(Instant.now().minusSeconds(3600)); // signed 1h ago
        OreSiUser user = activeUser(APP_ID, charteTs);
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, Map.of(), expiryTs);
        assertThat(result.isActiveApplicationUser()).isTrue();
    }

    @Test
    @DisplayName("isActiveApplicationUser false si charte signée AVANT la date de validité")
    void isActiveApplicationUserFalseWhenCharteBeforeExpiry() {
        Timestamp expiryTs = Timestamp.from(Instant.now().minusSeconds(3600)); // expiry 1h ago
        Timestamp charteTs = Timestamp.from(Instant.now().minusSeconds(7200)); // signed 2h ago (before expiry)
        OreSiUser user = activeUser(APP_ID, charteTs);
        ApplicationUserResult result = ApplicationUserResult.of(APP_ID, user, Map.of(), expiryTs);
        assertThat(result.isActiveApplicationUser()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────
    // getApplicationRoles
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getApplicationRoles() retourne 2 rôles SQL")
    void getApplicationRoles() {
        Application app = new Application();
        app.setName("testApp");
        app.setId(UUID.randomUUID());
        List<String> roles = ApplicationUserResult.getApplicationRoles(app);
        assertThat(roles).hasSize(2);
        assertThat(roles.stream().anyMatch(r -> r.contains(OreSiRightOnApplicationRole.APPLICATION_MANAGER))).isTrue();
        assertThat(roles.stream().anyMatch(r -> r.contains(OreSiRightOnApplicationRole.USER_MANAGER))).isTrue();
    }
}
