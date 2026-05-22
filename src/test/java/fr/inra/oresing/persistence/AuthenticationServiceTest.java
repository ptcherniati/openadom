package fr.inra.oresing.persistence;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Tag("SUITE")
@Tag("core.auth")
class AuthenticationServiceTest extends AbstractIntegrationTest {
    @Value("${spring.mail.from}")
    String mailFrom;

    @Autowired
    private JavaMailSender mailSender;


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSetRole() {
        OreSiRoleToAccessDatabase anonymousRole = authenticationService.setRole(OreSiRole.anonymous());
        assertEquals(OreSiRole.anonymous(), anonymousRole);
    }

    @Test
    void testCreateAndLogin() throws Throwable {
        final ArgumentCaptor<SimpleMailMessage> messageArgumentCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        final String login = "toto";
        final String email = "toto@codelutin.com";
        final String password = "xxxx";
        mockMvc.perform(
                        post("/api/v1/users")
                                .param("login", login)
                                .param("password", password)
                                .param("email", email)
                                .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();

        Mockito.verify(mailSender).send(messageArgumentCaptor.capture());
        SimpleMailMessage message = messageArgumentCaptor.getValue();
        assertArrayEquals(new String[]{email}, message.getTo());
        assertEquals(mailFrom, message.getFrom());
        // Extraction robuste : on cherche la ligne après le marqueur i18n
        // "Votre clé de validation est" plutôt qu'un index hardcodé qui
        // casse à chaque reformat du template ( cf incident b5407d6b ) .
        String validationKey = extractValidationKeyFromMail(message);
        String user = mockMvc.perform(put("/api/v1/users")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"verificationKey\": \"" + validationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();
        final String id = JsonPath.parse(user).read("$.userId", String.class);
        LoginAdminResult loginAdminResult = authenticationService.login(login, password);
        assertEquals(login, loginAdminResult.login());
        final OreSiUserRole userRole = authenticationService.getUserRole(UUID.fromString(id));

        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"email\": \"" + email + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();
        Mockito.verify(mailSender, Mockito.times(2)).send(messageArgumentCaptor.capture());
        message = messageArgumentCaptor.getValue();
        assertArrayEquals(new String[]{email}, message.getTo());
        assertEquals(mailFrom, message.getFrom());
        Objects.requireNonNull(message.getText()).split("\n");

        final String newEmail = "newmail@inrae.fr";
        validationKey = getValidationKey(messageArgumentCaptor, login, password, "pending", newEmail);

        //on valide l'email
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"verificationKey\": \"" + validationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();

        validationKey = getValidationKey(messageArgumentCaptor, login, password, "active", newEmail);
        final String validationKey2 = getValidationKey(messageArgumentCaptor, login, password, "active", newEmail);
        assertEquals(validationKey2, validationKey);
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\"" +
                                 ", \"email\": \"" + newEmail + "\", " +
                                 "\"newPassword\": \"newpassword\", " +
                                 "\"newPasswordConfirm\": \"newpassword\", " +
                                 "\"verificationKey\": \"" + validationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();

        //on se log avec le nouveau password
        loginAdminResult = authenticationService.login(login, "newpassword");
        assertEquals(login, loginAdminResult.login());


        authenticationService.setRole(userRole);
        authenticationService.resetRole();
        authenticationService.removeUser(loginAdminResult.id());
    }

    private String getValidationKey(final ArgumentCaptor<SimpleMailMessage> messageArgumentCaptor,
                                    final String login,
                                    final String password,
                                    final String expectedState,
                                    final String email) throws Exception {
        final String validationKey;
        final SimpleMailMessage message;
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is(expectedState)))
                .andReturn().getResponse().getContentAsString();
        Mockito.verify(mailSender, Mockito.times(3)).send(messageArgumentCaptor.capture());
        message = messageArgumentCaptor.getValue();
        assertArrayEquals(new String[]{email}, message.getTo());
        assertEquals(mailFrom, message.getFrom());
        validationKey = extractValidationKeyFromMail(message);
        return validationKey;
    }

    /**
     * Extraction robuste de la clé de validation depuis le corps du mail .
     * Cherche la ligne suivant le marqueur i18n {@code "Votre clé de
     * validation est"} ; survit à tout reformatage du template email
     * tant que le marqueur reste présent ( contrairement aux extractions
     * par index hardcodé {@code lines[6]} ou {@code lines.length - 3}
     * qui cassent à chaque ajustement d'espacement - cf incident commit
     * b5407d6b reformat email ) .
     */
    private static String extractValidationKeyFromMail(final SimpleMailMessage message) {
        final String body = Objects.requireNonNull(message.getText());
        final String[] lines = body.split("\n");
        final String marker = "Votre clé de validation est";
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].contains(marker)) {
                return lines[i + 1].trim();
            }
        }
        throw new AssertionError("Marqueur '" + marker
                + "' absent du corps de mail :\n" + body);
    }

    @Transactional
    void setToActive(final UUID userId) {
        namedParameterJdbcTemplate.update("update public.OreSiUser set accountstate = 'active' where id = :id", Map.of("id", userId));
    }
}
