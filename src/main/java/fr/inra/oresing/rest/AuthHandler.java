package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.model.OreSiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

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

    public static final String JWT_TOKEN = "si-ore-jwt";
    public static final String HTTP_CORRELATION_ID = "X-Correlation-ID";

    private Key key;

    @Value("${jwt.expiration:3600}")
    private int jwtExpiration;

    @Autowired
    private ObjectMapper json;

    public AuthHandler(@Value("${jwt.secret:1234567890AZERTYUIOP}") String jwtSecret) {
        while (jwtSecret.length() < 32) {
            jwtSecret += "0";
        }
        byte[] keyBytes = jwtSecret.getBytes();
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Lecture du cookie jwt et conservation du role de l'utilisateur
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientCorrelationId = request.getHeader(HTTP_CORRELATION_ID);
        OreSiUser user = getRole(request).orElse(null);
        log.debug("preHandle for " + user);

        OreSiContext context = new OreSiContext(user, clientCorrelationId);
        OreSiContext.set(context);

        return true;
    }

    /**
     * enregistrement du role de l'utilisateur dans un cookie
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        log.debug("postHandle for " + OreSiContext.get());
        Optional<Cookie> cookie = createCookie();
        cookie.ifPresent(response::addCookie);
        OreSiContext.reset();
    }

    protected Optional<OreSiUser> getRole(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        Optional<OreSiUser> result = Stream.of(cookies)
                .filter(cookie -> JWT_TOKEN.equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(this::getRoleFromJwt)
                .filter(Objects::nonNull)
                .findFirst();

        return result;
    }

    protected OreSiUser getRoleFromJwt(String token) {
        try {
            String jsonUser = Jwts.parser()
                    .setSigningKey(key)
                    .parseClaimsJws(token)
                    .getBody().getSubject();
            OreSiUser user = json.readValue(jsonUser, OreSiUser.class);
            return user;
        } catch (Exception eee) {
            log.error("can't decode jwt token: " + token, eee);
            return null;
        }
    }

    protected Optional<Cookie> createCookie() {
        try {
            String jsonUser = json.writeValueAsString(OreSiContext.get().getUser());

            String token = Jwts.builder()
                    .setSubject(jsonUser)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date((new Date()).getTime() + jwtExpiration * 1000))
                    .signWith(key)
                    .compact();
            Cookie result = new Cookie(JWT_TOKEN, token);
            result.setHttpOnly(true);

            return Optional.of(result);
        } catch (JsonProcessingException eee) {
            log.error("can't create cookie: ", eee);
            return Optional.empty();
        }
    }
}
