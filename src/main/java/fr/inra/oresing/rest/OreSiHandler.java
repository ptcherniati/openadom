package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiAnonymousRequestClient;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;

@Slf4j
@Component
public class OreSiHandler implements HandlerInterceptor {

    private static final String HTTP_CORRELATION_ID = "X-Correlation-ID";

    @Autowired
    private AuthHelper authHelper;

    @Autowired
    private OreSiApiRequestContext requestContext;

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        handleCorrelation(request, response);
        handleAuthentication(request, response);
        return true;
    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) throws Exception {
        log.debug("afterCompletion for role {}", getRole());
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private String getRole() {
        return Optional.ofNullable(requestContext)
                .map(OreSiApiRequestContext::getRequestClient)
                .map(OreSiRequestClient::role)
                .map(OreSiRole::getAsSqlRole)
                .orElse("with no role");
    }

    /**
     * Si un utilisateur est authentifié, on enregistre son rôle de le contexte
     */
    private void handleAuthentication(final HttpServletRequest request, final HttpServletResponse response) {

        // l'utiliateur authentifié, le cas échéant
        final Optional<OreSiUserRequestClient> userRequestClient = authHelper.initContext(request);

        // s'il est authentifié, on met à jours son cookie
        userRequestClient
                .ifPresent(authenticatedUser -> authHelper
                        .refreshCookie(response, isLogoutRequest(request)?null:authenticatedUser));

        // quoiqu'il en soit, on doit avoir un role pour accéder à la base
        final OreSiRequestClient requestClient;
        if (userRequestClient.isPresent()) {
            requestClient = userRequestClient.get();
        } else {
            requestClient = OreSiAnonymousRequestClient.ANONYMOUS;
        }
        requestContext.setRequestClient(requestClient);
    }private boolean isLogoutRequest(HttpServletRequest request) {
    return "DELETE".equalsIgnoreCase(request.getMethod())
           && request.getRequestURI().matches(".*/logout/?$");
}


    /**
     * On enregistre dans le contexte l'identifiant de correlation
     */
    private void handleCorrelation(final HttpServletRequest request, final HttpServletResponse response) {
        final String clientCorrelationId = request.getHeader(HTTP_CORRELATION_ID);
        requestContext.setClientCorrelationId(clientCorrelationId);
    }

    @Override
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final ModelAndView modelAndView) {
        log.debug("postHandle for role {}", getRole()
        );
        requestContext.reset();
    }

}
