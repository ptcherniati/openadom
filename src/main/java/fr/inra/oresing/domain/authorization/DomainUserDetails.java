package fr.inra.oresing.domain.authorization;

import java.util.Collection;
import java.util.List;

/**
 * Abstraction domaine pour la notion d'identité utilisateur — équivalent pur (sans Spring Security)
 * de {@code org.springframework.security.core.userdetails.UserDetails}.
 *
 * <p>Les implémentations concrètes (records {@code NotConnected*}) restent dans le domaine.
 * La couche REST fournit un adaptateur Spring Security si nécessaire.
 */
public interface DomainUserDetails {

    /**
     * @return les autorités accordées à cet utilisateur
     */
    @SuppressWarnings("java:S1452")
    default Collection<? extends DomainGrantedAuthority> getAuthorities() {
        return List.of();
    }

    /** @return le mot de passe haché (ou vide si non pertinent) */
    String getPassword();

    /** @return le nom d'utilisateur (login) */
    String getUsername();

    /** @return {@code true} si le compte n'est pas expiré */
    default boolean isAccountNonExpired() {
        return true;
    }

    /** @return {@code true} si le compte n'est pas verrouillé */
    default boolean isAccountNonLocked() {
        return true;
    }

    /** @return {@code true} si les credentials ne sont pas expirés */
    default boolean isCredentialsNonExpired() {
        return true;
    }

    /** @return {@code true} si l'utilisateur est actif */
    default boolean isEnabled() {
        return true;
    }
}