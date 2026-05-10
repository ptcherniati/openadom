package fr.inra.oresing.domain.authorization;

/**
 * Implémentation simple de {@link DomainGrantedAuthority} — équivalent domaine pur de
 * {@code org.springframework.security.core.authority.SimpleGrantedAuthority}.
 *
 * @param authority le nom textuel du rôle (ex. "ROLE_UNAUTHENTIFIED_UPDATE_USER")
 */
public record SimpleDomainGrantedAuthority(String authority) implements DomainGrantedAuthority {
    @Override
    public String getAuthority() {
        return authority;
    }
}