package fr.inra.oresing.rest.security;

import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUnauthentifiedUserForCreate;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.authentication.OreSiConfigAttribute;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class OreSiAuthorizationManager implements AuthenticationProvider, ServiceContainerBean {
    public static final GrantedAuthority ROLE_AUTHENTIFIED_USER = new SimpleGrantedAuthority("ROLE_AUTHENTIFIED_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_UPDATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_UPDATE_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_CREATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_CREATE_USER");

    private ServiceContainer serviceContainer;


    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return Optional.ofNullable(authentication)
                .map(auth -> {
                    List<ConfigAttribute> attributes = new ArrayList<>();
                    switch (auth) {
                        case OreSiAuthenticationToken authenticationToken -> {
                            if (authenticationToken.isLogin()) {
                                attributes.add(new OreSiConfigAttribute(ROLE_AUTHENTIFIED_USER.getAuthority()));
                            } else if (authenticationToken.isUpdate()) {
                                attributes.add(new OreSiConfigAttribute(ROLE_UNAUTHENTIFIED_UPDATE_USER.getAuthority()));
                            } else if (auth.getPrincipal() instanceof NotConnectedUnauthentifiedUserForCreate) {
                                attributes.add(new OreSiConfigAttribute(ROLE_UNAUTHENTIFIED_CREATE_USER.getAuthority()));
                            } else {
                                attributes.add(new OreSiConfigAttribute(ROLE_AUTHENTIFIED_USER.getAuthority()));
                            }
                        }
                        case null, default -> throw new AccessDeniedException("UnAuthorized");
                    }
                    return authentication;
                })
                .orElse(null);
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return OreSiAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
