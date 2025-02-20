package fr.inra.oresing.persistence;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.AuthHelper;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import org.hamcrest.Matchers;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, OreSiNg.MailSenderForTest.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("SUITE")
public class AuthenticationServiceTest {
    @Value("${spring.mail.from}")
    String mailFrom;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSetRole() {
        OreSiRoleToAccessDatabase anonymousRole = authenticationService.setRole(OreSiRole.anonymous());
        assertEquals(OreSiRole.anonymous(), anonymousRole);
    }

    @Test
    public void testCreateAndLogin() throws Throwable {
        final ArgumentCaptor<SimpleMailMessage> messageArgumentCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        final String login = "toto";
        final String email = "toto@codelutin.com";
        final String password = "xxxx";
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/v1/users")
                                .param("login", login)
                                .param("password", password)
                                .param("email", email)
                                .contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        Cookie cookie = response.getCookie(AuthHelper.JWT_COOKIE_NAME);
/*
        final String authUserId = JsonPath.parse(response.getContentAsString()).read("$.id", String.class);
*/
        Mockito.verify(mailSender).send(messageArgumentCaptor.capture());
        SimpleMailMessage message = messageArgumentCaptor.getValue();
        assertArrayEquals(new String[]{email}, message.getTo());
        assertEquals(mailFrom, message.getFrom());
        String[] lines = Objects.requireNonNull(message.getText()).split("\n");
        String validationKey = lines[6];
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
        final String[] lines;
        final String user;
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
        lines = Objects.requireNonNull(message.getText()).split("\n");
        validationKey = lines[lines.length - 3];
        return validationKey;
    }

    @Transactional
    void setToActive(final UUID userId) {
        namedParameterJdbcTemplate.update("update public.OreSiUser set accountstate = 'active' where id = :id", Map.of("id", userId));
    }
}
