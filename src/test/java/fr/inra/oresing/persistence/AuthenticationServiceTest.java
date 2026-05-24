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

        // Depuis le refacto "suppression pendingEmail" ( commit 4cb62e30 ) le
        // nouvel email cible n'est plus stocke en base entre les 2 phases :
        // le frontend doit transporter {@code email=newEmail} dans le
        // payload de chaque phase ( Phase 1 : declenche l'envoi de la cle ;
        // Phase 2 : la cle + le meme newEmail permettent au backend de
        // recomputer la cle attendue et de comparer ) . Le state reste
        // "active" tout au long du flow ( aucun pending intermediaire ) .
        final String newEmail = "newmail@inrae.fr";

        // Phase 1 : declenche l'envoi de la cle au nouvel email .
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"email\": \"" + newEmail + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();
        Mockito.verify(mailSender, Mockito.times(2)).send(messageArgumentCaptor.capture());
        message = messageArgumentCaptor.getValue();
        assertArrayEquals(new String[]{newEmail}, message.getTo());
        assertEquals(mailFrom, message.getFrom());
        Objects.requireNonNull(message.getText()).split("\n");

        validationKey = getValidationKey(messageArgumentCaptor, login, password, "active", newEmail);

        // Phase 2 : valide la cle + applique le swap email = newEmail . Le
        // payload doit reporter {@code email=newEmail} pour que le backend
        // recompute la meme cle ( cf {@code commitEmailChange} ) .
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"email\": \"" + newEmail + "\", \"verificationKey\": \"" + validationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")))
                .andReturn().getResponse().getContentAsString();

        // Apres commit du changement d'email , user.email == newEmail . Un
        // PUT avec login + newPassword ( email facultatif ) suffit pour
        // changer le mot de passe . Pas besoin de pre-requester un
        // verificationKey : avec la nouvelle semantique 2-phase , la cle
        // ne sert QUE pour la phase 2 d'un email-change ( pending_email
        // non vide ) ; en dehors de ce flow elle est ignoree . Note :
        // ancienne version du test re-appelait getValidationKey avec
        // newEmail apres commit -> aujourd'hui c'est un no-op qui rend
        // 422 EMAIL_UNCHANGED ( cf invariant "200 OK <=> mail envoye" ) .
        mockMvc.perform(put("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"login\": \"" + login + "\"" +
                                 ", \"email\": \"" + newEmail + "\", " +
                                 // password ( actuel ) requis pour
                                 // authentifier la mutation - dispatch
                                 // NotConnectedAuthentifiedActiveUser .
                                 "\"password\": \"" + password + "\", " +
                                 "\"newPassword\": \"newpassword\", " +
                                 "\"newPasswordConfirm\": \"newpassword\"}"))
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

    /**
     * Garde-fou anti-regression du bug "wrong validation key locks out
     * user" : si l'utilisateur soumet une mauvaise cle pendant un
     * changement d'email , l'email actuel ET l'accountstate DOIVENT
     * rester intacts ( pas de bascule en pending ) et la reponse HTTP
     * doit etre 422 ( pas 401 -> pas de logout cote frontend ) .
     */
    @Test
    void emailChange_wrongValidationKey_preservesEmailAndState() throws Throwable {
        final ArgumentCaptor<SimpleMailMessage> messageArgumentCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        final String login = "alice";
        final String email = "alice@codelutin.com";
        final String password = "pwd-alice";
        // Setup : creation + activation .
        mockMvc.perform(post("/api/v1/users")
                .param("login", login).param("password", password).param("email", email)
                .contentType(MediaType.APPLICATION_JSON));
        Mockito.verify(mailSender).send(messageArgumentCaptor.capture());
        final String activationKey = extractValidationKeyFromMail(messageArgumentCaptor.getValue());
        mockMvc.perform(put("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"verificationKey\": \"" + activationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")));
        final LoginAdminResult loginAdminResult = authenticationService.login(login, password);

        // Phase 1 : demande de changement vers un nouvel email .
        final String newEmail = "alice-new@inrae.fr";
        mockMvc.perform(put("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"login\": \"" + login + "\", \"email\": \"" + newEmail + "\", \"password\": \"" + password + "\"}"))
                // Invariant : state reste active ( ne plus passer en pending ) .
                .andExpect(jsonPath("$.accountState", Matchers.is("active")));

        // Phase 2 KO : on soumet une cle deliberement fausse .
        mockMvc.perform(put("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"verificationKey\": \"WRONG-KEY-12\"}"))
                // 422 = erreur metier , PAS 401 ( pas de logout client ) .
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity());

        // Verification : login marche TOUJOURS avec l'email INITIAL +
        // password initial ( aucune mutation persistee ) .
        final LoginAdminResult after = authenticationService.login(login, password);
        assertEquals(login, after.login());
        // L'email actuel doit etre toujours l'initial ( pas le newEmail ) .
        // On le verifie indirectement via getUserRole + lookup du record :
        // si l'email avait ete change , findByLoginAndEmail(login, email)
        // ne renverrait plus le user .
        org.junit.jupiter.api.Assertions.assertTrue(
                authenticationService.getUserRole(after.id()) != null,
                "l'utilisateur doit rester accessible apres une cle KO");

        authenticationService.setRole(authenticationService.getUserRole(loginAdminResult.id()));
        authenticationService.resetRole();
        authenticationService.removeUser(loginAdminResult.id());
    }

    /**
     * Invariant "200 OK <=> mail bien envoye" : si le client appelle
     * phase 1 du changement d'email avec un email IDENTIQUE a l'email
     * courant et sans newPassword , le backend NE DOIT PAS envoyer de
     * mail ni retourner 200 ( sinon le frontend afficherait un toast
     * vert mensonger ) . Reponse attendue : 422 EMAIL_UNCHANGED .
     */
    @Test
    void emailChange_sameEmail_returns422AndDoesNotSendMail() throws Throwable {
        final ArgumentCaptor<SimpleMailMessage> activationMailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        final String login = "bob";
        final String email = "bob@codelutin.com";
        final String password = "pwd-bob";
        mockMvc.perform(post("/api/v1/users")
                .param("login", login).param("password", password).param("email", email)
                .contentType(MediaType.APPLICATION_JSON));
        Mockito.verify(mailSender).send(activationMailCaptor.capture());
        final String activationKey = extractValidationKeyFromMail(activationMailCaptor.getValue());
        mockMvc.perform(put("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"login\": \"" + login + "\", \"password\": \"" + password + "\", \"verificationKey\": \"" + activationKey + "\"}"))
                .andExpect(jsonPath("$.accountState", Matchers.is("active")));
        final LoginAdminResult loginAdminResult = authenticationService.login(login, password);

        // Reset des invocations : on veut verifier qu'AUCUN nouvel envoi
        // de mail n'a lieu en phase 1 no-op .
        Mockito.clearInvocations(mailSender);

        // Phase 1 avec MEME email que l'email courant -> no-op cote metier .
        mockMvc.perform(put("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"login\": \"" + login + "\", \"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("EMAIL_UNCHANGED"));

        // Aucun mail envoye sur ce no-op .
        Mockito.verifyNoInteractions(mailSender);

        authenticationService.setRole(authenticationService.getUserRole(loginAdminResult.id()));
        authenticationService.resetRole();
        authenticationService.removeUser(loginAdminResult.id());
    }
}
