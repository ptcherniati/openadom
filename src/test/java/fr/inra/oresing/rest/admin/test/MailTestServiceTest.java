package fr.inra.oresing.rest.admin.test;

import fr.inra.oresing.config.AlertsProperties;
import fr.inra.oresing.domain.exceptions.MailServiceUnavailableException;
import fr.inra.oresing.mail.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link MailTestService} . Mocks via Mockito - aucun
 * contexte Spring , aucun SMTP reel .
 */
@Tag("core.config")
@DisplayName("MailTestService - resolution recipients + envoi + capture erreurs")
class MailTestServiceTest {

    private EmailService emailService;
    private AlertsProperties alertsProperties;
    private MailTestService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        alertsProperties = new AlertsProperties();
        service = new MailTestService(emailService, alertsProperties);
    }

    @Test
    @DisplayName("resolveRecipients : requete vide -> fallback alerts.env")
    void fallbackWhenRequestEmpty() {
        alertsProperties.setRecipients("admin@inrae.fr,ops@inrae.fr");
        List<String> resolved = service.resolveRecipients(null);
        assertEquals(List.of("admin@inrae.fr", "ops@inrae.fr"), resolved);
    }

    @Test
    @DisplayName("resolveRecipients : requete fournie -> non-vide gagne")
    void requestOverridesFallback() {
        alertsProperties.setRecipients("admin@inrae.fr");
        List<String> resolved = service.resolveRecipients(List.of("user@inrae.fr"));
        assertEquals(List.of("user@inrae.fr"), resolved);
    }

    @Test
    @DisplayName("resolveRecipients : filtre les entrees vides")
    void filterEmpty() {
        List<String> resolved = service.resolveRecipients(List.of("a@b.fr", "", "  ", "c@d.fr"));
        assertEquals(List.of("a@b.fr", "c@d.fr"), resolved);
    }

    @Test
    @DisplayName("defaultSample retourne valeurs par defaut")
    void defaultSampleHasDefaults() {
        alertsProperties.setRecipients("admin@inrae.fr");
        MailTestRequest sample = service.defaultSample();
        assertEquals(List.of("admin@inrae.fr"), sample.recipients());
        assertEquals(MailTestService.DEFAULT_SUBJECT, sample.subject());
        assertEquals(MailTestService.DEFAULT_BODY, sample.body());
    }

    @Test
    @DisplayName("send : succes -> MailTestResult.success=true + appelle sendEmail par destinataire")
    void sendSuccess() {
        MailTestRequest req = new MailTestRequest(
                List.of("a@b.fr", "c@d.fr"), "subj", "body");
        MailTestResult res = service.send(req);
        assertTrue(res.success());
        assertEquals(List.of("a@b.fr", "c@d.fr"), res.recipients());
        assertTrue(res.durationMs() >= 0);
        assertNull(res.errorMessage());
        verify(emailService).sendEmail(eq("admin-test"), eq("a@b.fr"), eq("subj"), eq("body"));
        verify(emailService).sendEmail(eq("admin-test"), eq("c@d.fr"), eq("subj"), eq("body"));
    }

    @Test
    @DisplayName("send : aucun destinataire -> KO sans appel SMTP")
    void sendNoRecipients() {
        MailTestRequest req = new MailTestRequest(null, "s", "b");
        MailTestResult res = service.send(req);
        assertFalse(res.success());
        assertNotNull(res.errorMessage());
        assertTrue(res.errorMessage().contains("Aucun destinataire"));
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("send : EmailService throws -> KO + message capture")
    void sendCapturesException() {
        doThrow(new MailServiceUnavailableException("SMTP HS", null))
                .when(emailService).sendEmail(anyString(), anyString(), anyString(), anyString());
        MailTestRequest req = new MailTestRequest(List.of("a@b.fr"), "s", "b");
        MailTestResult res = service.send(req);
        assertFalse(res.success());
        assertTrue(res.errorMessage().contains("SMTP HS"));
    }

    @Test
    @DisplayName("send : subject blank -> utilise default subject")
    void sendBlankSubjectFallback() {
        MailTestRequest req = new MailTestRequest(List.of("a@b.fr"), "  ", "body");
        service.send(req);
        verify(emailService).sendEmail(anyString(), anyString(),
                eq(MailTestService.DEFAULT_SUBJECT), eq("body"));
    }
}
