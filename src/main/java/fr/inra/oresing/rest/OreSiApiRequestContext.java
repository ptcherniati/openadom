package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public class OreSiApiRequestContext {
    public static Optional<OreSiAuthenticationToken> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast);
    }

    private static Optional<OreSiAuthenticationToken> getAuthenticationTokenOptional() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast);
    }

    public static OreSiAuthenticationToken getAuthenticationToken() {
        return getAuthenticationTokenOptional().orElse(null);
    }

    public static void setAuthenticationToken(OreSiAuthenticationToken authenticationToken) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.getContextHolderStrategy().setContext(
                context
        );
        //SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    public static OreSiRequestClient getRequestClient() {
        return getRequestClientOptional()
                .orElse(null);
    }

    private static Optional<OreSiUserRequestClient> getRequestClientOptional() {
        return getAuthenticationTokenOptional()
                .map(OreSiAuthenticationToken::getRequestClient);
    }

    public static UUID getRequestUserId() {
        return getRequestClientOptional()
                .map(OreSiRequestClient::id)
                .orElse(null);
    }

    public static OreSiRoleToAccessDatabase getRequestClientRole() {
        return getRequestClientOptional()
                .map(OreSiRequestClient::role)
                .orElse(null);
    }
}