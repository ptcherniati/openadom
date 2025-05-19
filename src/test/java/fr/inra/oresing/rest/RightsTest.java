package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.OpenAdomJwtValue;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.security.JWTExtractor;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("integration.rest")
@Slf4j
public class RightsTest {
    @Value("${jwt.secret:1234567890AZERTYUIOP}")
    String jwtSecret = "1234567890AZERTYUIOP";
    SecretKey key;
    String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');

    private Fixtures fixtures;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    @BeforeEach
    public void init() throws Exception {
        this.key = Keys.hmacShaKeyFor(secureEnoughJwtSecret.getBytes());
        fixtures = new Fixtures(
                mockMvc,
                userRepository,
                namedParameterJdbcTemplate,
                authenticationService
        );
    }

    @Test
    void noCookieTest() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void timeOutCookie() throws Exception {
        OreSiUser oreSiUser = new OreSiUser();
        final UUID authUserId = fixtures.adminConnection.userResult().userId();
        oreSiUser.setId(authUserId);
        OreSiUserRequestClient oreSiUserRequestClient = new OreSiUserRequestClient(authUserId, OreSiUserRole.forUser(oreSiUser));
        Cookie cookie = newCookie(oreSiUserRequestClient);
        try {
            mockMvc.perform(get("/api/v1/applications")
                    .cookie(cookie));
            Assertions.fail();
        } catch (AuthenticationCredentialsNotFoundException e) {
            Assertions.assertTrue(e.getCause() instanceof ExpiredJwtException);
        }
    }

    private Cookie newCookie(final OreSiUserRequestClient requestClient) {
        final String json;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            final OpenAdomJwtValue jwtCookieValue = new OpenAdomJwtValue(requestClient);
            json = objectMapper.writeValueAsString(jwtCookieValue);
        } catch (final JsonProcessingException e) {
            throw new SiOreIllegalArgumentException(
                    "requestMapperSerializationError",
                    Map.of(
                            "requestClient", requestClient,
                            "objectMapper", objectMapper,
                            "message", e.getLocalizedMessage()
                    )
            );
            //throw new OreSiTechnicalException("impossible de sérialiser " + requestClient + " avec " + objectMapper, e);
        }
        final Date issuedAt = new Date();

        final String token = Jwts.builder()
                .subject(json)
                .issuedAt(issuedAt)
                .expiration(DateUtils.addSeconds(issuedAt, 0))
                .signWith(key)
                .compact();

        final Cookie cookie = new Cookie(JWTExtractor.JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        return cookie;
    }

    //@Test
    void logoutShouldInvalidateSession() throws Exception {
        // Étape 1: Vérifier que l'utilisateur est bien connecté en accédant à /applications
        mockMvc.perform(get("/api/v1/applications")
                        .cookie(fixtures.adminConnection.cookie()))
                .andExpect(status().isOk()); // Devrait renvoyer 200 OK car l'utilisateur est connecté

        // Étape 2: Déconnexion de l'utilisateur
        mockMvc.perform(delete("/api/v1/logout").with(csrf().asHeader())
                        .cookie(fixtures.adminConnection.cookie()))
                .andExpect(status().isOk()); // La déconnexion devrait réussir

        // Récupérer le cookie de déconnexion (qui devrait être expiré)
        Cookie deconnectedCookie = mockMvc.perform(delete("/api/v1/logout").with(csrf().asHeader())
                        .cookie(fixtures.adminConnection.cookie()))
                .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);

        Assertions.assertNull(deconnectedCookie, "Le cookie de déconnexion dpit être null");
    }

    @Test
    void cookieMaxAgeIsResetOnEachCall() throws Exception {
        // Étape 1: Vérifier que l'utilisateur est bien connecté en accédant à /applications
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .cookie(fixtures.adminConnection.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        // Récupérer le cookie après le premier appel
        Cookie firstCallCookie = result.getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        Assertions.assertNotNull(firstCallCookie, "Le cookie ne devrait pas être null");
        Assertions.assertTrue(firstCallCookie.getMaxAge() > 0, "Le cookie devrait avoir une durée de vie positive");

        // Étape 2: Répéter l'appel pour vérifier que le maxAge est réinitialisé
        result = mockMvc.perform(get("/api/v1/applications")
                        .cookie(firstCallCookie))
                .andExpect(status().isOk())
                .andReturn();

        // Récupérer le cookie après le deuxième appel
        Cookie secondCallCookie = result.getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        Assertions.assertNotNull(secondCallCookie, "Le cookie ne devrait pas être null");
        Assertions.assertTrue(secondCallCookie.getMaxAge() > 0, "Le cookie devrait avoir une durée de vie positive");

        // Vérifier que le maxAge a été réinitialisé
        Assertions.assertTrue(secondCallCookie.getMaxAge() >= fixtures.adminConnection.cookie().getMaxAge(), "Le maxAge devrait être réinitialisé");
    }

}