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
            Function<UUID, OreSiUserRole> getUserRole,
            JsonRowMapper<OreSiUserRequestClient> mapper,
            int jwtExpiration,
            String jwtSecret) {
        this.getUserRole = getUserRole;
        this.mapper = mapper;
        final String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');
        final byte[] keyBytes = secureEnoughJwtSecret.getBytes();
        key = Keys.hmacShaKeyFor(keyBytes);
        JWTExtractor.jwtExpiration = jwtExpiration;
    }

    public static void addJwtHeader(HttpServletResponse response, String jwt) {
        response.setHeader(AUTHORIZATION, BEARER_ + jwt);
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
        if (token.startsWith("Bearer ")) {
            token = token.substring(7); // on enlève 'Bearer '
        }
        try {
            json = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (ExpiredJwtException ex) {
            throw new BadCredentialsException("expired JWT", ex);
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid JWT", ex);
        } catch (SignatureException ex) {
            throw new BadCredentialsException("Invalid JWT", ex);
        } catch (JwtException ex) {
            throw new AuthenticationCredentialsNotFoundException("Invalid JWT", ex);
        }

        return mapper.readValue(json, OpenAdomJwtValue.class).requestClient();
    }

    public String refreshJwtInResponse(HttpServletResponse response, UUID id, boolean isSecureEnvironnement) {
        OreSiUserRole userRole = getUserRole.apply(id);
        OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(id, userRole);
        String json = mapper.toJson(new OpenAdomJwtValue(requestClient));
        String jwt = buildToken(json);
        try {
            addJwtHeader(response, jwt);
        } catch (Exception e) {
            log.trace("pas grave");
        }
        return jwt;
    }

    public void setSetGetUserRole(Function<UUID, OreSiUserRole> getUserRole) {
        this.getUserRole = getUserRole;
    }


    protected void clearSession(HttpServletRequest request, HttpServletResponse response, boolean isSecureEnvironnement) {

        // Invalider la session côté serveur
        request.getSession().invalidate();

        Cookie cookie = new Cookie(JWT_COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}