package fr.inra.oresing.mail.rightsrequest;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link RightsRequestNotificationService} (#487 Phase 1).
 *
 * <p>Couverture :
 * <ul>
 *     <li>1 mail au demandeur + N mails aux gestionnaires de l'application cible</li>
 *     <li>déduplication d'un utilisateur à la fois applicationManager et userManager</li>
 *     <li>aucun mail aux gestionnaires d'autres applications (filtrage par rôle SQL)</li>
 *     <li>fallback locale FR pour toute langue non supportée</li>
 *     <li>fire-and-forget : un échec SMTP n'interrompt pas la chaîne et est journalisé</li>
 *     <li>aucun mail si l'application est nulle</li>
 * </ul>
 *
 * <p>L'exécutor utilisé en test est synchrone ({@code Runnable::run}) pour rendre
 * les vérifications déterministes.
 */
class RightsRequestNotificationServiceTest {

    private static final String APP_NAME = "ticket_507";
    private static final String FRONT_BASE_URL = "https://localhost";

    private JavaMailSender mailSender;
    private UserRepository userRepository;
    private AuthenticationService authenticationService;
    private Executor synchronousExecutor;

    private RightsRequestNotificationService service;

    private Application application;
    private UUID applicationId;
    private OreSiUser requester;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        userRepository = mock(UserRepository.class);
        authenticationService = mock(AuthenticationService.class);
        synchronousExecutor = Runnable::run;

        service = new RightsRequestNotificationService(
                mailSender, userRepository, authenticationService, synchronousExecutor);
        ReflectionTestUtils.setField(service, "mailFrom", "openadom@inrae.fr");
        ReflectionTestUtils.setField(service, "frontBaseUrl", FRONT_BASE_URL);

        applicationId = UUID.randomUUID();
        application = new Application();
        application.setId(applicationId);
        application.setName(APP_NAME);

        requester = newUser("rachid", "rachid@example.org");
    }

    @Test
    @DisplayName("Envoie 1 mail au demandeur et N mails aux gestionnaires de cette application")
    void sends_one_mail_to_requester_and_one_per_manager() {
        OreSiUser appManager = newUser("alice", "alice@example.org");
        OreSiUser userManager = newUser("bob", "bob@example.org");
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of(appManager));
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of(userManager));

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "je veux un accès", Locale.FRENCH);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(3)).send(captor.capture());
        List<SimpleMailMessage> sent = captor.getAllValues();

        assertThat(sent).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("rachid@example.org", "alice@example.org", "bob@example.org");
        assertThat(sent).allSatisfy(m -> assertThat(m.getFrom()).isEqualTo("openadom@inrae.fr"));

        // L'utilisateur courant DB est bien restauré après la résolution des destinataires
        verify(authenticationService).setRoleAdmin();
        verify(authenticationService).setRoleForClient();
    }

    @Test
    @DisplayName("Un même utilisateur applicationManager + userManager n'est notifié qu'une fois")
    void deduplicates_user_with_both_manager_roles() {
        OreSiUser dual = newUser("alice", "alice@example.org");
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of(dual));
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of(dual));

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        // 1 demandeur + 1 manager dédupliqué = 2 mails
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Le lien de traitement contient l'URL absolue construite depuis frontBaseUrl")
    void manager_mail_body_contains_absolute_treatment_url() {
        UUID requestId = UUID.randomUUID();
        OreSiUser manager = newUser("alice", "alice@example.org");
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of(manager));
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, requestId, requester, "comment", Locale.FRENCH);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        SimpleMailMessage managerMail = captor.getAllValues().stream()
                .filter(m -> "alice@example.org".equals(m.getTo()[0]))
                .findFirst().orElseThrow();
        String expectedUrl = FRONT_BASE_URL + "/applications/" + APP_NAME + "/authorizationsRequest/treatment/" + requestId;
        assertThat(managerMail.getText()).contains(expectedUrl);
    }

    @Test
    @DisplayName("Une locale non supportée retombe sur FR")
    void unknown_locale_falls_back_to_french() {
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of());
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.JAPANESE);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("Votre demande d'accès");
    }

    @Test
    @DisplayName("Locale anglaise sélectionne le template EN")
    void english_locale_uses_english_template() {
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of());
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.ENGLISH);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("Your access request");
    }

    @Test
    @DisplayName("Un échec SMTP sur un destinataire n'empêche pas les autres envois")
    void smtp_failure_is_logged_and_does_not_break_other_sends() {
        OreSiUser manager = newUser("alice", "alice@example.org");
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of(manager));
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        // Premier envoi (demandeur) lève, le second (manager) doit quand même être tenté
        doThrow(new MailSendException("smtp down"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Application nulle : aucun envoi, aucun lookup de rôle")
    void null_application_short_circuits() {
        service.notifyRequestSubmitted(null, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        verify(userRepository, never()).findUsersGrantedRole(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Destinataire sans email est ignoré (warn) sans interrompre les autres")
    void recipient_without_email_is_skipped() {
        OreSiUser noEmailManager = newUser("nomail", null);
        OreSiUser realManager = newUser("alice", "alice@example.org");
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of(noEmailManager, realManager));
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        // 1 demandeur + 1 manager (le sans-mail est skippé)
        verify(mailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(m -> m.getTo()[0])
                .containsExactlyInAnyOrder("rachid@example.org", "alice@example.org");
    }

    @Test
    @DisplayName("Aucun gestionnaire : seul le demandeur reçoit un mail")
    void no_manager_means_only_requester_is_notified() {
        when(userRepository.findUsersGrantedRole(any())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Le périmètre des destinataires est strictement scoppé à l'application demandée")
    void only_managers_of_target_application_are_queried() {
        when(userRepository.findUsersGrantedRole(adminRoleName())).thenReturn(List.of());
        when(userRepository.findUsersGrantedRole(userManagerRoleName())).thenReturn(List.of());

        service.notifyRequestSubmitted(application, UUID.randomUUID(), requester, "ok", Locale.FRENCH);

        // Vérifie que les 2 lookups portent bien sur les rôles de CETTE application
        verify(userRepository).findUsersGrantedRole(eq(adminRoleName()));
        verify(userRepository).findUsersGrantedRole(eq(userManagerRoleName()));
        // Et qu'aucun autre rôle n'a été interrogé
        verify(userRepository, times(2)).findUsersGrantedRole(any());
    }

    private String adminRoleName() {
        return applicationId + "_applicationManager";
    }

    private String userManagerRoleName() {
        return applicationId + "_userManager";
    }

    private OreSiUser newUser(final String login, final String email) {
        OreSiUser user = new OreSiUser();
        user.setId(UUID.randomUUID());
        user.setLogin(login);
        user.setEmail(email);
        return user;
    }
}