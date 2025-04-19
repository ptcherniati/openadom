package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;
import java.util.UUID;

@Component
@RequestScope
public class OreSiApiRequestContext {
    public OreSiAuthenticationToken getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(OreSiAuthenticationToken authenticationToken) {
        this.authenticationToken = authenticationToken;
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationToken);
        SecurityContextHolder.getContextHolderStrategy().setContext(
                context
        );
    }

    private OreSiAuthenticationToken authenticationToken;

    public OreSiRequestClient getRequestClient() {
        return getRequestClientOptional()
                .orElse(null);
    }

    private Optional<OreSiUserRequestClient> getRequestClientOptional() {
        return Optional.ofNullable(authenticationToken)
                .map(OreSiAuthenticationToken::getrequestClient);
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