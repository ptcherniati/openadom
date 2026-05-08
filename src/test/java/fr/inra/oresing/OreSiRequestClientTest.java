package fr.inra.oresing;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static fr.inra.oresing.OreSiRequestClient.DISCONECTED_EXCEPTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("core.basic")
class OreSiRequestClientTest {
    static OreSiRequestClientForTest client = new OreSiRequestClientForTest();

    @Test
    void id() {
        assertThatThrownBy(() -> client.id())
                .isInstanceOf(DisconnectedException.class)
                .hasMessageContaining(DISCONECTED_EXCEPTION);
    }

    @Test
    void role() {
        Assertions.assertThat(client.role()).isEqualTo(OreSiRole.anonymous());
    }

    // ─── OreSiAnonymousRequestClient ─────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.DisplayName("OreSiAnonymousRequestClient.ANONYMOUS.role() retourne anonymous()")
    void anonymousClientRole() {
        Assertions.assertThat(OreSiAnonymousRequestClient.ANONYMOUS.role())
                .isEqualTo(OreSiRole.anonymous());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("OreSiAnonymousRequestClient.toString() n'est pas null")
    void anonymousClientToString() {
        Assertions.assertThat(OreSiAnonymousRequestClient.ANONYMOUS.toString()).isNotBlank();
    }

    static class OreSiRequestClientForTest implements OreSiRequestClient {

    }
}