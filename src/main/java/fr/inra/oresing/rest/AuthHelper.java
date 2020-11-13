package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.OreSiUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class AuthHelper {

    public static final String JWT_COOKIE_NAME = "si-ore-jwt";

    private final Key key;

    @Value("${jwt.expiration:3600}")
    private int jwtExpiration;

    @Autowired
    private ObjectMapper objectMapper;

    public AuthHelper(@Value("${jwt.secret:1234567890AZERTYUIOP}") String jwtSecret) {
        String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');
        byte[] keyBytes = secureEnoughJwtSecret.getBytes();
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Optional<OreSiUser> initContext(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (ArrayUtils.isEmpty(cookies)) {
            if (log.isDebugEnabled()) {
                log.debug("aucun cookie détecté dans la requête, c'est possible si on est sur login");
            }
            String pathInfo = request.getPathInfo();
            Preconditions.checkState(pathInfo.endsWith("/login"), "tentative d'accéder à " + pathInfo + " sans cookie");
            return Optional.empty();
        }
        Cookie cookie = Stream.of(cookies)
                .filter(aCookie -> JWT_COOKIE_NAME.equals(aCookie.getName()))
                .findAny()
                .orElseThrow(() -> new OreSiTechnicalException("cookie attendu dans " + request));
        OreSiUser user = getRoleFromJwt(cookie);
        return Optional.of(user);
    }

    public void refreshCookie(HttpServletResponse response, OreSiUser oreSiUser) {
        Cookie cookie = newCookie(oreSiUser);
        response.addCookie(cookie);
    }

    private OreSiUser getRoleFromJwt(Cookie cookie) {
        String token = cookie.getValue();
        String json = Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        OreSiUser oreSiUser;
        try {
            oreSiUser = objectMapper.readValue(json, OreSiUser.class);
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de désérialiser " + json + " avec " + objectMapper, e);
        }
        return oreSiUser;
    }

    private Cookie newCookie(OreSiUser oreSiUser) {
        String json;
        try {
            json = objectMapper.writeValueAsString(oreSiUser);
        } catch (JsonProcessingException e) {
            throw new OreSiTechnicalException("impossible de sérialiser " + oreSiUser + " avec " + objectMapper, e);
        }
        String token = Jwts.builder()
                .setSubject(json)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpiration * 1000))
                .signWith(key)
                .compact();
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

}
