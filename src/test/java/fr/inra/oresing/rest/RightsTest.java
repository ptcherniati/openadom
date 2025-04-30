package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.*;
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
import org.junit.jupiter.api.*;
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
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("integration.rest")
@Slf4j
public class RightsTest {
    @Autowired
    private Fixtures fixtures;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private UUID authUserId;
    private Cookie authCookie;
    @Value("${jwt.secret:1234567890AZERTYUIOP}")
    String jwtSecret ="1234567890AZERTYUIOP";
    SecretKey key;
    String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');

    @BeforeEach
    public void createUser() throws Exception {
        this.key = Keys.hmacShaKeyFor(secureEnoughJwtSecret.getBytes());
        CreateUserResult authUser;
        try {
            final OreSiUser user = authenticationService.getByIdOrLogin("poussin");
            authUser = CreateUserResult.of(user);
        } catch (final Exception e) {
            authUser = createUserIfNotExists("poussin", "xxxxxxxx", "poussin@inrae.fr");
        }
        authUserId = authUser.userId();
        setToActive(authUserId);
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        addRoleAdmin(authUser);
    }

    private CreateUserResult createUserIfNotExists(String login, String password, String mail) throws Exception {
        if (mockMvc.perform(post("/api/v1/login")
                        .param("login", login)
                        .param("password", password))
                .andReturn()
                .getResponse().getStatus() > 300) {
            return authenticationService.createUser(login, password, mail);
        } else {
            OreSiUser userByLogin = userRepository.findByLogin(login).orElse(null);
            return CreateUserResult.of(Objects.requireNonNull(userByLogin));
        }

    }

    @Transactional
    void setToActive(final UUID userId) {
        namedParameterJdbcTemplate.update(
                """
                        UPDATE public.oresiuser SET accountstate = 'active' WHERE id = :id
                        """, Map.of("id", userId));
    }

    @Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        namedParameterJdbcTemplate.update("grant \"openAdomAdmin\" to \"" + dbUserResult.userId().toString() + "\" WITH INHERIT TRUE;", Map.of());
    }

    @Test
    public void noCookieTest() throws Exception {
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void timeOutCookie() throws Exception {
        OreSiUser oreSiUser = new OreSiUser();
        oreSiUser.setId(authUserId);
        OreSiUserRequestClient oreSiUserRequestClient = new OreSiUserRequestClient(authUserId, OreSiUserRole.forUser(oreSiUser));
        Cookie cookie = newCookie(oreSiUserRequestClient);
        try {
            mockMvc.perform(get("/api/v1/applications")
                    .cookie(cookie));
            fail();
        } catch (AuthenticationCredentialsNotFoundException e) {
            assertTrue(e.getCause() instanceof ExpiredJwtException);
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

        final String token =  Jwts.builder()
                .subject(json)
                .issuedAt(issuedAt)
                .expiration(DateUtils.addSeconds(issuedAt, 0))
                .signWith(key)
                .compact();

        final Cookie cookie = new Cookie(JWTExtractor.JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    @Test
    public void logoutShouldInvalidateSession() throws Exception {
        // Étape 1: Vérifier que l'utilisateur est bien connecté en accédant à /applications
        mockMvc.perform(get("/api/v1/applications")
                        .cookie(authCookie))
                .andExpect(status().isOk()); // Devrait renvoyer 200 OK car l'utilisateur est connecté

        // Étape 2: Déconnexion de l'utilisateur
        mockMvc.perform(delete("/api/v1/logout")
                        .cookie(authCookie))
                .andExpect(status().isOk()); // La déconnexion devrait réussir

        // Récupérer le cookie de déconnexion (qui devrait être expiré)
        authCookie = mockMvc.perform(delete("/api/v1/logout")
                        .cookie(authCookie))
                .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);

        Assertions.assertNull(authCookie, "Le cookie de déconnexion dpit être null");
    }

    @Test
    public void cookieMaxAgeIsResetOnEachCall() throws Exception {
        // Étape 1: Vérifier que l'utilisateur est bien connecté en accédant à /applications
        MvcResult result = mockMvc.perform(get("/api/v1/applications")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn();

        // Récupérer le cookie après le premier appel
        authCookie = result.getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        Assertions.assertNotNull(authCookie, "Le cookie ne devrait pas être null");
        Assertions.assertTrue(authCookie.getMaxAge() > 0, "Le cookie devrait avoir une durée de vie positive");

        // Étape 2: Répéter l'appel pour vérifier que le maxAge est réinitialisé
        result = mockMvc.perform(get("/api/v1/applications")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn();

        // Récupérer le cookie après le deuxième appel
        Cookie cookie2 = result.getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        Assertions.assertNotNull(cookie2, "Le cookie ne devrait pas être null");
        Assertions.assertTrue(cookie2.getMaxAge() > 0, "Le cookie devrait avoir une durée de vie positive");

        // Vérifier que le maxAge a été réinitialisé
        Assertions.assertTrue(cookie2.getMaxAge() >= authCookie.getMaxAge(), "Le maxAge devrait être réinitialisé");
    }

}
