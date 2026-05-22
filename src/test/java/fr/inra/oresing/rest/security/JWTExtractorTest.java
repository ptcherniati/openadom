package fr.inra.oresing.rest.security;

import fr.inra.oresing.OpenAdomJwtValue;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@Tag("core.auth")
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests unitaires de JWTExtractor")
class JWTExtractorTest {

    private static final String TEST_SECRET = "test-secret-key-minimum-32-chars!!";
    private static final int TEST_EXPIRATION = 3600;

    @Mock
    private AuthenticationService authenticationService;

    private JsonRowMapper<?> realMapper;
    private JWTExtractor jwtExtractor;

    @BeforeEach
    void setUp() {
        realMapper = new JsonRowMapper<>();
        jwtExtractor = new JWTExtractor(authenticationService, realMapper, TEST_EXPIRATION, TEST_SECRET);
    }

    // ------------------------------------------------------------------ //
    //  extractJwtCookie                                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("extractJwtCookie")
    class ExtractJwtCookie {

        @Test
        @DisplayName("extrait le token depuis l'en-tête Authorization: Bearer")
        void fromAuthorizationHeader() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Bearer myTestToken");

            String result = jwtExtractor.extractJwtCookie(request);
            assertEquals("myTestToken", result);
        }

        @Test
        @DisplayName("extrait le token depuis le cookie si pas d'en-tête")
        void fromCookie() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);
            Cookie cookie = new Cookie(JWTExtractor.JWT_COOKIE_NAME, "cookieToken");
            when(request.getCookies()).thenReturn(new Cookie[]{cookie});

            assertEquals("cookieToken", jwtExtractor.extractJwtCookie(request));
        }

        @Test
        @DisplayName("retourne null si pas d'en-tête et pas de cookie correspondant")
        void returnsNullWhenNoCookieOrHeader() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);
            when(request.getCookies()).thenReturn(null);

            assertNull(jwtExtractor.extractJwtCookie(request));
        }

        @Test
        @DisplayName("retourne null si l'en-tête Authorization ne commence pas par 'Bearer '")
        void returnsNullWhenHeaderNotBearer() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn("Basic someCredentials");
            when(request.getCookies()).thenReturn(null);

            assertNull(jwtExtractor.extractJwtCookie(request));
        }

        @Test
        @DisplayName("ignore les cookies avec un nom différent")
        void ignoresOtherCookies() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Authorization")).thenReturn(null);
            Cookie otherCookie = new Cookie("other-cookie", "otherValue");
            when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

            assertNull(jwtExtractor.extractJwtCookie(request));
        }
    }

    // ------------------------------------------------------------------ //
    //  buildToken                                                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("buildToken")
    class BuildTokenTests {

        @Test
        @DisplayName("génère un token JWT non-vide à partir d'un sujet JSON")
        void generatesNonEmptyToken() {
            String token = JWTExtractor.buildToken("{\"test\":\"value\"}");
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("le token généré contient trois segments JWT séparés par des points")
        void tokenHasThreeJwtSegments() {
            String token = JWTExtractor.buildToken("{}");
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "Un JWT doit avoir 3 segments : header.payload.signature");
        }

        @Test
        @DisplayName("deux appels consécutifs produisent des tokens différents (horodatage)")
        void consecutiveCallsProduceDifferentTokens() throws InterruptedException {
            String token1 = JWTExtractor.buildToken("{}");
            // Pause minimale pour distinguer les horodatages
            Thread.sleep(1050);
            String token2 = JWTExtractor.buildToken("{}");
            assertNotEquals(token1, token2);
        }
    }

    // ------------------------------------------------------------------ //
    //  getRequestClientFromJwt                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getRequestClientFromJwt")
    class GetRequestClientFromJwt {

        @Test
        @DisplayName("retourne le RequestClient encodé dans un JWT valide")
        void returnsClientFromValidJwt() throws IOException {
            UUID userId = UUID.randomUUID();
            OreSiUserRole role = new OreSiUserRole();
            OreSiUserRequestClient client = new OreSiUserRequestClient(userId, role);
            OpenAdomJwtValue jwtValue = new OpenAdomJwtValue(client);
            String json = realMapper.toJson(jwtValue);
            String token = JWTExtractor.buildToken(json);

            OreSiUserRequestClient result = jwtExtractor.getRequestClientFromJwt(token);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
        }

        @Test
        @DisplayName("accepte un token préfixé par 'Bearer '")
        void acceptsBearerPrefixedToken() throws IOException {
            UUID userId = UUID.randomUUID();
            OreSiUserRequestClient client = new OreSiUserRequestClient(userId, new OreSiUserRole());
            String json = realMapper.toJson(new OpenAdomJwtValue(client));
            String token = "Bearer " + JWTExtractor.buildToken(json);

            OreSiUserRequestClient result = jwtExtractor.getRequestClientFromJwt(token);
            assertThat(result.id()).isEqualTo(userId);
        }

        @Test
        @DisplayName("lève BadCredentialsException pour un token invalide")
        void throwsBadCredentialsForInvalidToken() {
            assertThrows(BadCredentialsException.class,
                    () -> jwtExtractor.getRequestClientFromJwt("completely.invalid.token"));
        }

        @Test
        @DisplayName("lève BadCredentialsException pour un token malformé (pas 3 segments)")
        void throwsBadCredentialsForMalformedToken() {
            assertThrows(BadCredentialsException.class,
                    () -> jwtExtractor.getRequestClientFromJwt("notavalidjwt"));
        }

        @Test
        @DisplayName("lève BadCredentialsException pour un token expiré")
        void throwsBadCredentialsForExpiredToken() {
            // Créer un token expiré manuellement avec une date d'expiration dans le passé
            String expiredToken = Jwts.builder()
                    .subject("{}")
                    .issuedAt(new Date(System.currentTimeMillis() - 10000))
                    .expiration(new Date(System.currentTimeMillis() - 5000))  // expiré il y a 5s
                    .signWith(JWTExtractor.key)
                    .compact();

            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> jwtExtractor.getRequestClientFromJwt(expiredToken));
            assertThat(ex.getCause()).isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        }

        @Test
        @DisplayName("lève BadCredentialsException pour un token signé avec une autre clé")
        void throwsBadCredentialsForWrongSignature() {
            // Créer un token avec une clé différente
            String wrongKey = StringUtils.rightPad("wrong-secret-key", 32, '0');
            String tokenWrongSignature = Jwts.builder()
                    .subject("{}")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(Keys.hmacShaKeyFor(wrongKey.getBytes()))
                    .compact();

            assertThrows(BadCredentialsException.class,
                    () -> jwtExtractor.getRequestClientFromJwt(tokenWrongSignature));
        }
    }

    // ------------------------------------------------------------------ //
    //  addJwtHeader (méthode statique)                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("addJwtHeader")
    class AddJwtHeader {

        @Test
        @DisplayName("ajoute l'en-tête Authorization sur la réponse")
        void addsAuthorizationHeader() {
            HttpServletResponse response = mock(HttpServletResponse.class);
            JWTExtractor.addJwtHeader(response, "Bearer someToken");
            verify(response).setHeader("Authorization", "Bearer someToken");
        }
    }

    // ------------------------------------------------------------------ //
    //  refreshJwtInResponse                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("refreshJwtInResponse")
    class RefreshJwtInResponse {

        @Test
        @DisplayName("génère un nouveau JWT et l'ajoute dans la réponse HTTP")
        void generatesJwtAndSetsHeader() {
            UUID userId = UUID.randomUUID();
            OreSiUserRole userRole = new OreSiUserRole();
            when(authenticationService.getUserRole(userId)).thenReturn(userRole);

            HttpServletResponse response = mock(HttpServletResponse.class);
            String jwt = jwtExtractor.refreshJwtInResponse(response, userId);

            assertThat(jwt).isNotBlank();
            verify(response).setHeader(eq("Authorization"), eq(jwt));
        }
    }

    // ------------------------------------------------------------------ //
    //  clearSession                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("clearSession")
    class ClearSession {

        @Test
        @DisplayName("invalide la session et ajoute un cookie expiré pour effacer le JWT")
        void invalidatesSessionAndExpiresCookie() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            HttpSession session = mock(HttpSession.class);
            when(request.getSession()).thenReturn(session);

            jwtExtractor.clearSession(request, response);

            verify(session).invalidate();
            verify(response).addCookie(argThat(cookie ->
                    JWTExtractor.JWT_COOKIE_NAME.equals(cookie.getName())
                    && cookie.getMaxAge() == 0
                    && cookie.isHttpOnly()
                    && "/".equals(cookie.getPath())
            ));
        }
    }
}