package fr.inra.oresing.domain.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link SimpleDomainGrantedAuthority}.
 */
@Tag("domain.model")
@DisplayName("SimpleDomainGrantedAuthority – getAuthority, equals, hashCode")
class SimpleDomainGrantedAuthorityTest {

    @Test
    @DisplayName("getAuthority() retourne la valeur du constructeur")
    void getAuthority() {
        SimpleDomainGrantedAuthority auth = new SimpleDomainGrantedAuthority("ROLE_ADMIN");
        assertThat(auth.getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("authority() (accessor record) est identique à getAuthority()")
    void accessorEqualsGetter() {
        SimpleDomainGrantedAuthority auth = new SimpleDomainGrantedAuthority("ROLE_USER");
        assertThat(auth.authority()).isEqualTo(auth.getAuthority());
    }

    @Test
    @DisplayName("implémente DomainGrantedAuthority")
    void implementsInterface() {
        assertThat(new SimpleDomainGrantedAuthority("X")).isInstanceOf(DomainGrantedAuthority.class);
    }

    @Test
    @DisplayName("equals et hashCode cohérents (record)")
    void equalsAndHashCode() {
        SimpleDomainGrantedAuthority a1 = new SimpleDomainGrantedAuthority("ROLE_A");
        SimpleDomainGrantedAuthority a2 = new SimpleDomainGrantedAuthority("ROLE_A");
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    @DisplayName("deux autorités différentes ne sont pas égales")
    void notEqual() {
        SimpleDomainGrantedAuthority a1 = new SimpleDomainGrantedAuthority("ROLE_A");
        SimpleDomainGrantedAuthority a2 = new SimpleDomainGrantedAuthority("ROLE_B");
        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    @DisplayName("toString contient l'authority")
    void toStringContainsAuthority() {
        SimpleDomainGrantedAuthority auth = new SimpleDomainGrantedAuthority("ROLE_MANAGER");
        assertThat(auth.toString()).contains("ROLE_MANAGER");
    }
}
