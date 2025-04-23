package fr.inra.oresing.rest.security;

import fr.inra.oresing.OpenAdomJwtValue;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.JsonRowMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@Slf4j
public class JWTExtractor {
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_ = "Bearer ";
    public static final String JWT_COOKIE_NAME = "si-ore-jwt";
    public static SecretKey key;
    public static int jwtExpiration;
    private final JsonRowMapper<OreSiUserRequestClient> mapper;
    private Function<UUID, OreSiUserRole> getUserRole;

    public JWTExtractor(
            JsonRowMapper<OreSiUserRequestClient> mapper,
            int jwtExpiration,
            String jwtSecret) {
        this.mapper = mapper;
        final String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');
        final byte[] keyBytes = secureEnoughJwtSecret.getBytes();
        key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpiration = jwtExpiration;
    }

    public String extractJwtCookie(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_)) {
            return authHeader.substring(7);
        }
        return Optional.ofNullable(request.getCookies())
                .map(Arrays::asList)
                .stream()
                .flatMap(List::stream)
                .filter(cookie -> cookie.getName().equals(JWT_COOKIE_NAME))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public OreSiUserRequestClient getRequestClientFromJwt(String token) throws IOException {
        String json;
        try {
            json = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException ex) {
            throw new AuthenticationCredentialsNotFoundException("JWT expiré", ex);
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("JWT invalide", ex);
        } catch (SignatureException ex) {
            throw new BadCredentialsException("Signature JWT invalide", ex);
        } catch (JwtException ex) {
            throw new AuthenticationCredentialsNotFoundException("Erreur d'authentification JWT", ex);
        }

        return mapper.readValue(json, OpenAdomJwtValue.class).requestClient();
    }

    public void refreshJwtInResponse(HttpServletResponse response, UUID id) {
        OreSiUserRole userRole = getUserRole.apply(id);
        OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(id, userRole);
        String json = mapper.toJson(new OpenAdomJwtValue(requestClient));
        String jwt = buildToken(json);
        try {
            addCookie(jwt, response);
            addJwtHeader(response, jwt);
        } catch (Exception e) {
            log.trace("pas grave");
        }
    }

    public static void addJwtHeader(HttpServletResponse response, String jwt) {
        response.setHeader("Authorization", "Bearer " + jwt);
    }

    public static void addCookie(String jwt, HttpServletResponse response) {
        Cookie cookie = getCookie(jwt);
        response.addCookie(cookie);
    }

    public static Cookie getCookie(String jwt) {
        final Cookie cookie = new Cookie(JWT_COOKIE_NAME, jwt);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtExpiration);
        return cookie;
    }

    public static String buildToken(String json) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .subject(json)
                .issuedAt(issuedAt)
                .expiration(DateUtils.addSeconds(issuedAt, jwtExpiration))
                .signWith(key)
                .compact();
    }

    public void setSetGetUserRole(Function<UUID, OreSiUserRole> getUserRole) {
        this.getUserRole = getUserRole;
    }
}