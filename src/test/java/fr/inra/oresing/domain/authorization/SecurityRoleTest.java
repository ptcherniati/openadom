package fr.inra.oresing.domain.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs de {@link SecurityRole} — aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("SecurityRole — constantes")
class SecurityRoleTest {

    @Test
    @DisplayName("ROLE_AUTHENTIFIED_USER_VALUE est correct")
    void roleAuthentifiedUser() {
        assertThat(SecurityRole.ROLE_AUTHENTIFIED_USER_VALUE).isEqualTo("ROLE_AUTHENTIFIED_USER");
    }

    @Test
    @DisplayName("ROLE_UNAUTHENTIFIED_UPDATE_USER_VALUE est correct")
    void roleUnauthentifiedUpdateUser() {
        assertThat(SecurityRole.ROLE_UNAUTHENTIFIED_UPDATE_USER_VALUE)
                .isEqualTo("ROLE_UNAUTHENTIFIED_UPDATE_USER");
    }

    @Test
    @DisplayName("ROLE_UNAUTHENTIFIED_CREATE_USER_VALUE est correct")
    void roleUnauthentifiedCreateUser() {
        assertThat(SecurityRole.ROLE_UNAUTHENTIFIED_CREATE_USER_VALUE)
                .isEqualTo("ROLE_UNAUTHENTIFIED_CREATE_USER");
    }
}
