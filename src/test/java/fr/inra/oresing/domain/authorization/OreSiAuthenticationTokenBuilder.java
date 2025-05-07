package fr.inra.oresing.domain.authorization;

import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class OreSiAuthenticationTokenBuilder {
    public static final OreSiAuthenticationTokenBuilder builder() {
        return new OreSiAuthenticationTokenBuilder();
    }

    private Object principal;
    private String credentials;
    private Collection<? extends GrantedAuthority> authorities;

    public OreSiAuthenticationToken build() {
        return new OreSiAuthenticationToken(
                principal,
                credentials,
                authorities
        );
    }
}