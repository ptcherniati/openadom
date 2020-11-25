package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiAnonymousRequestClient;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        handleCorrelation(request, response);
        handleAuthentication(request, response);
        return true;
    }

    /**
     * Si un utilisateur est authentifié, on enregistre son rôle de le contexte
     */
    private void handleAuthentication(HttpServletRequest request, HttpServletResponse response) {

        // l'utiliateur authentifié, le cas échéant
        Optional<OreSiUserRequestClient> userRequestClient = authHelper.initContext(request);

        // s'il est authentifié, on met à jours son cookie
        userRequestClient.ifPresent(authenticatedUser -> authHelper.refreshCookie(response, authenticatedUser));

        // quoiqu'il en soit, on doit avoir un role pour accéder à la base
        OreSiRequestClient requestClient;
        if (userRequestClient.isPresent()) {
            requestClient = userRequestClient.get();
        } else {
            requestClient = OreSiAnonymousRequestClient.ANONYMOUS;
        }
        requestContext.setRequestClient(requestClient);
    }

    /**
     * On enregistre dans le contexte l'identifiant de correlation
     */
    private void handleCorrelation(HttpServletRequest request, HttpServletResponse response) {
        String clientCorrelationId = request.getHeader(HTTP_CORRELATION_ID);
        requestContext.setClientCorrelationId(clientCorrelationId);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("postHandle for " + requestContext);
        requestContext.reset();
    }

}
