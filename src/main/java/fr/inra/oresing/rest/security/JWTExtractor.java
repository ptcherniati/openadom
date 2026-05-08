package fr.inra.oresing.rest.security;

import fr.inra.oresing.OpenAdomJwtValue;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

@Component
public class JWTExtractor {
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_ = "Bearer ";
    public static final String JWT_COOKIE_NAME = "si-ore-jwt";
    public static SecretKey key;
    public static int jwtExpiration;
    private final JsonRowMapper<?> mapper;
    private final Function<UUID, OreSiUserRole> getUserRole;

    /**
     * Longueur minimale du secret JWT en octets ( HS256 -> 256 bits = 32 octets ).
     * En dessous, la lib Jwts refuse la cle ; on impose ici un fail-fast explicite
     * pour eviter le pattern padding-avec-zeros qui produisait une cle devinable.
     */
    static final int MIN_JWT_SECRET_BYTES = 32;

    public JWTExtractor(
            AuthenticationService authenticationService,
            JsonRowMapper<?> mapper,
            @Value("${jwt.expiration:3600}") int jwtExpiration,
            @Value("${jwt.secret:}") String jwtSecret) {
        this.getUserRole = authenticationService::getUserRole;
        this.mapper = mapper;
        final byte[] keyBytes = validateAndDecodeSecret(jwtSecret);
        key = Keys.hmacShaKeyFor(keyBytes);
        JWTExtractor.jwtExpiration = jwtExpiration;
    }

    /**
     * Valide le secret JWT injecte par configuration et le convertit en octets.
     *
     * <p>Refuse les valeurs absentes, vides ou plus courtes que {@link #MIN_JWT_SECRET_BYTES}
     * apres encodage UTF-8. Ce contrat ferme la breche connue ou un fallback constant
     * ( "1234567890AZERTYUIOP" ) plus un padding par des '0' produisaient une cle
     * de signature triviale a forger.</p>
     *
     * @throws IllegalStateException si le secret est manquant ou trop faible ;
     *                               le contexte Spring refuse de demarrer.
     */
    private static byte[] validateAndDecodeSecret(final String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Property 'jwt.secret' (env JWT_SECRET) is required. "
                    + "Generate one via: openssl rand -base64 48");
        }
        final byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Property 'jwt.secret' must be at least " + MIN_JWT_SECRET_BYTES
                    + " bytes (got " + bytes.length + "). "
                    + "Generate one via: openssl rand -base64 48");
        }
        return bytes;
    }

    public static void addJwtHeader(HttpServletResponse response, String jwt) {
        response.setHeader(AUTHORIZATION, jwt);
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
        if (token.startsWith(BEARER_)) {
            token = token.substring(BEARER_.length());
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
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException | SignatureException ex) {
            throw new BadCredentialsException("Invalid JWT", ex);
        } catch (JwtException ex) {
            throw new AuthenticationCredentialsNotFoundException("Invalid JWT", ex);
        }

        return mapper.readValue(json, OpenAdomJwtValue.class).requestClient();
    }

    public String refreshJwtInResponse(HttpServletResponse response, UUID id) {
        OreSiUserRole userRole = getUserRole.apply(id);
        OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(id, userRole);
        String json = mapper.toJson(new OpenAdomJwtValue(requestClient));
        String jwt = buildToken(json);
        addJwtHeader(response, jwt);
        return jwt;
    }


    protected void clearSession(HttpServletRequest request, HttpServletResponse response) {

        // Invalider la session côté serveur
        request.getSession().invalidate();

        Cookie cookie = new Cookie(JWT_COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}