package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.JwtCookieValue;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.OreSiUserRequestClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
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

    public Optional<OreSiUserRequestClient> initContext(HttpServletRequest request) {
        Cookie[] cookies = ObjectUtils.firstNonNull(request.getCookies(), new Cookie[]{});
        return Stream.of(cookies)
                .filter(aCookie -> JWT_COOKIE_NAME.equals(aCookie.getName()))
                .findAny()
                .map(this::getRequestClientFromJwt);
    }

    public void refreshCookie(HttpServletResponse response, OreSiUserRequestClient requestClient) {
        Cookie cookie = newCookie(requestClient);
        response.addCookie(cookie);
    }

    private OreSiUserRequestClient getRequestClientFromJwt(Cookie cookie) {
        String token = cookie.getValue();
        String json = Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        OreSiUserRequestClient requestClient;
        try {
            JwtCookieValue jwtCookieValue = objectMapper.readValue(json, JwtCookieValue.class);
            requestClient = jwtCookieValue.getRequestClient();
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de désérialiser " + json + " avec " + objectMapper, e);
        }
        return requestClient;
    }

    private Cookie newCookie(OreSiUserRequestClient requestClient) {
        String json;
        try {
            JwtCookieValue jwtCookieValue = new JwtCookieValue();
            jwtCookieValue.setRequestClient(requestClient);
            json = objectMapper.writeValueAsString(jwtCookieValue);
        } catch (JsonProcessingException e) {
            throw new OreSiTechnicalException("impossible de sérialiser " + requestClient + " avec " + objectMapper, e);
        }
        Date issuedAt = new Date();
        String token = Jwts.builder()
                .setSubject(json)
                .setIssuedAt(issuedAt)
                .setExpiration(DateUtils.addSeconds(issuedAt, jwtExpiration))
                .signWith(key)
                .compact();
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

}
