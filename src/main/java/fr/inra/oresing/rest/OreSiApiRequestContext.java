package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class OreSiApiRequestContext {
    public OreSiAuthenticationToken getAuthenticationToken() {
        return getAuthenticationTokenOptional().orElse(null);
    }

    public void setAuthenticationToken(OreSiAuthenticationToken authenticationToken) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.getContextHolderStrategy().setContext(
                context
        );
        //SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    public OreSiRequestClient getRequestClient() {
        return getRequestClientOptional()
                .orElse(null);
    }

    private Optional<OreSiUserRequestClient> getRequestClientOptional() {
        return getAuthenticationTokenOptional()
                .map(OreSiAuthenticationToken::getrequestClient);
    }

    private static Optional<OreSiAuthenticationToken> getAuthenticationTokenOptional() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast);
    }

    public UUID getRequestUserId(){
        return getRequestClientOptional()
                .map(OreSiRequestClient::id)
                .orElse(null);
    }

    public OreSiRoleToAccessDatabase getRequestClientRole() {
        return getRequestClientOptional()
                .map(OreSiRequestClient::role)
                .orElse(null);
    }
}