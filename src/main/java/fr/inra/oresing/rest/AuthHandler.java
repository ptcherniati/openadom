package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.model.OreSiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Key;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
