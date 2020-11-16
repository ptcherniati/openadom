package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class OreSiHandler implements HandlerInterceptor {

    private static final String HTTP_CORRELATION_ID = "X-Correlation-ID";

    @Autowired
    private AuthHelper authHelper;

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
        OreSiRequestClient requestClient = authHelper.initContext(request);
        if (requestClient.isAnonymous()) {
            // rien à faire
        } else {
            authHelper.refreshCookie(response, requestClient);
        }
        OreSiApiRequestContext.get().setRequestClient(requestClient);
    }

    /**
     * On enregistre dans le contexte l'identifiant de correlation
     */
    private void handleCorrelation(HttpServletRequest request, HttpServletResponse response) {
        String clientCorrelationId = request.getHeader(HTTP_CORRELATION_ID);
        OreSiApiRequestContext.get().setClientCorrelationId(clientCorrelationId);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("postHandle for " + OreSiApiRequestContext.get());
        OreSiApiRequestContext.reset();
    }

}
