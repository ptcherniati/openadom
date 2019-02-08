package fr.inra.oresing.rest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
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

//    public static final String ANONYMOUS = "anonymous";
//    public static final String JWT_TOKEN = "si-ore-jwt";
//    public static final String HTTP_CORRELATION_ID = "X-Correlation-ID";
//
//    private Key key;
//
//    @Value("${jwt.expiration:3600}")
//    private int jwtExpiration;
//
//    public AuthHandler(@Value("${jwt.secret:1234567890AZERTYUIOP}") String jwtSecret) {
//        byte[] keyBytes = jwtSecret.getBytes();
//        key = Keys.hmacShaKeyFor(keyBytes);
//    }
//
//    /**
//     * Lecture du cookie jwt et conservation du role de l'utilisateur
//     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
//        String clientCorrelationId = request.getHeader(HTTP_CORRELATION_ID);
//        String role = getRole(request).orElse(ANONYMOUS);
//
//        OreSiContext context = new OreSiContext(role, clientCorrelationId);
//        OreSiContext.set(context);
//
//        return true;
//    }
//
//    /**
//     * enregistrement du role de l'utilisateur dans un cookie
//     */
//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
//        Cookie cookie = createCookie();
//        response.addCookie(cookie);
//        OreSiContext.reset();
//    }
//
//    protected Optional<String> getRole(HttpServletRequest request) {
//        Cookie[] cookies = request.getCookies();
//        if (cookies == null) {
//            return Optional.empty();
//        }
//
//        Optional<String> result = Stream.of(cookies)
//                .filter(cookie -> JWT_TOKEN.equals(cookie.getName()))
//                .map(Cookie::getValue)
//                .map(this::getRoleFromJwt)
//                .filter(Objects::nonNull)
//                .findFirst();
//
//        return result;
//    }
//
//    protected String getRoleFromJwt(String token) {
//        try {
//            return Jwts.parser()
//                    .setSigningKey(key)
//                    .parseClaimsJws(token)
//                    .getBody().getSubject();
//        } catch (Exception eee) {
//            log.error("can't decode jwt token: " + token);
//            return null;
//        }
//    }
//
//    protected Cookie createCookie() {
//        String token = Jwts.builder()
//                .setSubject(OreSiContext.get().getRole())
//                .setIssuedAt(new Date())
//                .setExpiration(new Date((new Date()).getTime() + jwtExpiration * 1000))
//                .signWith(key)
//                .compact();
//        Cookie result = new Cookie(JWT_TOKEN, token);
//        result.setHttpOnly(true);
//
//        return result;
//    }
}
