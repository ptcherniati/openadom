package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.OpenAdomJwtValue;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration.rest")
@Slf4j
public class RightsTest extends AbstractIntegrationTest {
    @Value("${jwt.secret:1234567890AZERTYUIOP}")
    String jwtSecret = "1234567890AZERTYUIOP";
    SecretKey key;
    String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');


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
    void timeOutBearer() throws Exception {
        OreSiUser oreSiUser = new OreSiUser();
        final UUID authUserId = fixtures.adminConnection.userResult().userId();
        oreSiUser.setId(authUserId);
        OreSiUserRequestClient oreSiUserRequestClient = new OreSiUserRequestClient(authUserId, OreSiUserRole.forUser(oreSiUser));
        String token = newJwt(oreSiUserRequestClient); // nouvelle méthode : retourne juste la chaîne JWT

        try {
            mockMvc.perform(get("/api/v1/applications")
                    .header("Authorization", "Bearer " + token));
            Assertions.fail();
        } catch (BadCredentialsException e) {
            Assertions.assertInstanceOf(ExpiredJwtException.class, e.getCause());
        }
    }


    private String newJwt(final OreSiUserRequestClient requestClient) {
        ObjectMapper objectMapper = new ObjectMapper();
        String json;
        try {
            OpenAdomJwtValue jwtValue = new OpenAdomJwtValue(requestClient);
            json = objectMapper.writeValueAsString(jwtValue);
        } catch (JsonProcessingException e) {
            throw new SiOreIllegalArgumentException("requestMapperSerializationError",
                    Map.of("requestClient", requestClient, "objectMapper", objectMapper, "message", e.getLocalizedMessage()));
        }
        Date issuedAt = new Date();
        Date expiry = new Date(issuedAt.getTime() - 1000); // On force l'expiration, pour tester le timeout (sinon mets une date passée)
        return Jwts.builder()
                .subject(json)
                .issuedAt(issuedAt)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }


    //@Test
    void logoutShouldInvalidateSession() throws Exception {
        // Étape 1: Vérifier que l'utilisateur est bien connecté en accédant à /applications
        mockMvc.perform(get("/api/v1/applications")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().isOk()); // Devrait renvoyer 200 OK car l'utilisateur est connecté

        // Étape 2: Déconnexion de l'utilisateur
        mockMvc.perform(delete("/api/v1/logout")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().isOk()); // La déconnexion devrait réussir

        // Récupérer le cookie de déconnexion (qui devrait être expiré)
        String deconnectedCookie = mockMvc.perform(delete("/api/v1/logout")

                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))


                .andReturn().getResponse().getHeader("Authorization");

        Assertions.assertNull(deconnectedCookie, "Le cookie de déconnexion dpit être null");
    }

}