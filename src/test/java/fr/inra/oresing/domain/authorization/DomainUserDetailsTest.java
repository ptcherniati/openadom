package fr.inra.oresing.domain.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des méthodes default de {@link DomainUserDetails}.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("DomainUserDetails — méthodes default de l'interface")
class DomainUserDetailsTest {

    /** Implémentation minimale pour tester les méthodes default. */
    private static final DomainUserDetails ANON = new DomainUserDetails() {
        @Override
        public String getPassword() {
            return "";
        }

        @Override
        public String getUsername() {
            return "anon";
        }
    };

    @Test
    @DisplayName("getAuthorities() retourne une collection vide par défaut")
    void defaultGetAuthoritiesIsEmpty() {
        Collection<? extends DomainGrantedAuthority> authorities = ANON.getAuthorities();
        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("isAccountNonExpired() retourne true par défaut")
    void defaultIsAccountNonExpiredTrue() {
        assertThat(ANON.isAccountNonExpired()).isTrue();
    }

    @Test
    @DisplayName("isAccountNonLocked() retourne true par défaut")
    void defaultIsAccountNonLockedTrue() {
        assertThat(ANON.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("isCredentialsNonExpired() retourne true par défaut")
    void defaultIsCredentialsNonExpiredTrue() {
        assertThat(ANON.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("isEnabled() retourne true par défaut")
    void defaultIsEnabledTrue() {
        assertThat(ANON.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getUsername() retourne la valeur fournie")
    void usernameIsReturned() {
        assertThat(ANON.getUsername()).isEqualTo("anon");
    }
}
