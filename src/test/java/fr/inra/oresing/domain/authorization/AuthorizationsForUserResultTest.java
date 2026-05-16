package fr.inra.oresing.domain.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("domain.model")
@DisplayName("AuthorizationsForUserResult — record + Roles enum")
class AuthorizationsForUserResultTest {

    @Test
    @DisplayName("Constructor assigns all fields")
    void constructor() {
        Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> authorizations = Map.of();
        AuthorizationsForUserResult result = new AuthorizationsForUserResult(
                authorizations, "myApp", true, "user-123");

        assertThat(result.applicationName()).isEqualTo("myApp");
        assertThat(result.isAdministrator()).isTrue();
        assertThat(result.userId()).isEqualTo("user-123");
        assertThat(result.authorizations()).isSameAs(authorizations);
    }

    @Test
    @DisplayName("Roles enum contains all expected values")
    void rolesEnumValues() {
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
    @DisplayName("Roles.valueOf works for DELETE")
    void rolesValueOf() {
        assertThat(AuthorizationsForUserResult.Roles.valueOf("DELETE"))
                .isEqualTo(AuthorizationsForUserResult.Roles.DELETE);
    }
}
