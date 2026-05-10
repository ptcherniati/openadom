package fr.inra.oresing.domain.authorization;

/**
 * Abstraction domaine pour la notion de rôle/autorisation accordé à un utilisateur.
 * Équivalent pur (sans Spring Security) de {@code org.springframework.security.core.GrantedAuthority}.
 */
@FunctionalInterface
public interface DomainGrantedAuthority {
    /**
     * @return le nom textuel de l'autorisation (ex. "ROLE_AUTHENTIFIED_USER")
     */
    String getAuthority();
}