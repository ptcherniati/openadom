package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.JwtCookieValue;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class AuthHelper {

    public static final String JWT_COOKIE_NAME = "si-ore-jwt";

    private final SecretKey key;

    @Value("${jwt.expiration:3600}")
    private int jwtExpiration;

    @Autowired
    private ObjectMapper objectMapper;

    public AuthHelper(@Value("${jwt.secret:1234567890AZERTYUIOP}") final String jwtSecret) {
        super();
        final String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');
        final byte[] keyBytes = secureEnoughJwtSecret.getBytes();
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Optional<OreSiUserRequestClient> initContext(final HttpServletRequest request) {
        final Cookie[] cookies = ObjectUtils.firstNonNull(request.getCookies(), new Cookie[]{});
        return Stream.of(cookies)
                .filter(aCookie -> JWT_COOKIE_NAME.equals(aCookie.getName()))
                .findAny()
                .map(this::getRequestClientFromJwt);
    }

    public void refreshCookie(final HttpServletResponse response, final OreSiUserRequestClient requestClient) {
        final Cookie cookie = newCookie(requestClient);
        response.addCookie(cookie);
    }

    private OreSiUserRequestClient getRequestClientFromJwt(final Cookie cookie) {
        final String token = cookie.getValue();
        final String json = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        final OreSiUserRequestClient requestClient;
        try {
            final JwtCookieValue jwtCookieValue = objectMapper.readValue(json, JwtCookieValue.class);
            requestClient = jwtCookieValue.requestClient();
        } catch (final IOException e) {
            throw new SiOreIllegalArgumentException(
                    "jsonDeserializationError",
                    Map.of(
                          "json",   json,
                            "objectMapper", objectMapper,
                            "message", e.getLocalizedMessage()
                    )
            );
           // throw new OreSiTechnicalException("impossible de désérialiser " + json + " avec " + objectMapper, e);
        }
        return requestClient;
    }

    private Cookie newCookie(final OreSiUserRequestClient requestClient) {
        final String json;
        try {
            final JwtCookieValue jwtCookieValue = new JwtCookieValue(requestClient);
            json = objectMapper.writeValueAsString(jwtCookieValue);
        } catch (final JsonProcessingException e) {
            throw new SiOreIllegalArgumentException(
                    "requestMapperSerializationError",
                    Map.of(
                          "requestClient",   requestClient,
                            "objectMapper", objectMapper,
                            "message", e.getLocalizedMessage()
                    )
            );
            //throw new OreSiTechnicalException("impossible de sérialiser " + requestClient + " avec " + objectMapper, e);
        }
        final Date issuedAt = new Date();
        final String token = Jwts.builder()
                .setSubject(json)
                .setIssuedAt(issuedAt)
                .setExpiration(DateUtils.addSeconds(issuedAt, jwtExpiration))
                .signWith(key)
                .compact();
        final Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(jwtExpiration);
        return cookie;
    }

}