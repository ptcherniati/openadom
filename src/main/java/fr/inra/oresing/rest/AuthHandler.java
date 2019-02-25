package fr.inra.oresing.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuthHandler implements HandlerInterceptor {

    @Autowired
    private AuthHelper authHelper;

    /**
     * Lecture du cookie jwt et conservation du role de l'utilisateur
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        authHelper.initContext(request);
        authHelper.refreshCookie(response);
        return true;
    }

    /**
     * enregistrement du role de l'utilisateur dans un cookie
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("postHandle for " + OreSiContext.get());
        authHelper.cleanContext();
    }

}
