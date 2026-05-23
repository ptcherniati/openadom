package fr.inra.oresing.mail;

import fr.inra.oresing.domain.exceptions.MailServiceUnavailableException;
import fr.inra.oresing.rest.services.ServiceContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.servlet.LocaleResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires d'integrite du service mail . Verifient :
 * <ul>
 *   <li>resilience SMTP : retry immediat + traduction en exception
 *       metier ( 503 ) sur double echec ;</li>
 *   <li>fail-fast sur recipient/From/Subject manquants ( evite que
 *       les programmer-errors deviennent des 500 cryptiques ) ;</li>
 *   <li>defense contre l'injection d'entetes SMTP via CR/LF .</li>
 * </ul>
 *
 * <p>Mockito.mock du {@link JavaMailSender} : pas besoin de demarrer
 * Spring ni de SMTP reel . Le {@link ServiceContainer} est mocke
 * vide ; les methodes touchees ne s'en servent pas .
 */
@Tag("core.mail")
class EmailServiceHardeningTest {

    private JavaMailSender mailSender;
    private EmailService service;

    @BeforeEach
    void setUp() {
        mailSender = Mockito.mock(JavaMailSender.class);
        final LocaleResolver localeResolver = Mockito.mock(LocaleResolver.class);
        final ServiceContainer serviceContainer = Mockito.mock(ServiceContainer.class);
        service = new EmailService(mailSender, localeResolver, serviceContainer, "openadom@test.local");
    }

    @Test
    void sendEmail_retryOnceOnFailure_thenSucceeds() {
        // Premiere tentative throw , seconde OK -> pas d'exception remontee .
        Mockito.doThrow(new MailSendException("transient"))
                .doNothing()
                .when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        service.sendEmail("alice", "alice@example.com", "Subject", "Body");

        Mockito.verify(mailSender, Mockito.times(2)).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_doubleFailure_throwsMailServiceUnavailable() {
        Mockito.doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        final MailServiceUnavailableException ex = assertThrows(
                MailServiceUnavailableException.class,
                () -> service.sendEmail("alice", "alice@example.com", "Subject", "Body"));
        assertTrue(ex.getMessage().contains("alice@example.com"),
                "message d'erreur doit citer le recipient pour le diagnostic");
    }

    @Test
    void sendEmail_recipientNull_throwsBeforeSmtpCall() {
        // Recipient null -> fail-fast , aucun appel SMTP .
        assertThrows(MailServiceUnavailableException.class,
                () -> service.sendEmail("alice", null, "Subject", "Body"));
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void sendEmail_recipientBlank_throwsBeforeSmtpCall() {
        assertThrows(MailServiceUnavailableException.class,
                () -> service.sendEmail("alice", "  ", "Subject", "Body"));
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void sendEmail_subjectNull_throwsBeforeSmtpCall() {
        assertThrows(MailServiceUnavailableException.class,
                () -> service.sendEmail("alice", "alice@example.com", null, "Body"));
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void sendEmail_crlfInSubject_isSanitized() {
        // Tentative d'injection : un attaquant pourrait essayer d'ajouter
        // un header Bcc via CR-LF . On verifie que le subject envoye au
        // SMTP ne contient plus aucun CR/LF .
        final org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.doNothing().when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        service.sendEmail("alice", "alice@example.com", "Hello\r\nBcc: attacker@evil.tld", "Body");

        Mockito.verify(mailSender).send(captor.capture());
        final String subject = captor.getValue().getSubject();
        assertEquals(-1, subject.indexOf('\r'), "CR strippe du subject");
        assertEquals(-1, subject.indexOf('\n'), "LF strippe du subject");
    }

    @Test
    void sendEmail_crlfInRecipient_isSanitized() {
        final org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.doNothing().when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        service.sendEmail("alice", "alice@example.com\r\nBcc: evil@x.tld", "Subject", "Body");

        Mockito.verify(mailSender).send(captor.capture());
        for (final String to : captor.getValue().getTo()) {
            assertEquals(-1, to.indexOf('\r'));
            assertEquals(-1, to.indexOf('\n'));
        }
    }

    @Test
    void sendEmail_fromUsesInjectedMailFrom() {
        final org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.doNothing().when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        service.sendEmail("alice", "alice@example.com", "Subject", "Body");

        Mockito.verify(mailSender).send(captor.capture());
        assertEquals("openadom@test.local", captor.getValue().getFrom());
    }
}
