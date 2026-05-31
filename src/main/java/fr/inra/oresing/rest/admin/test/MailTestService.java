package fr.inra.oresing.rest.admin.test;

import fr.inra.oresing.config.AlertsProperties;
import fr.inra.oresing.domain.exceptions.MailServiceUnavailableException;
import fr.inra.oresing.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service d'envoi d'un mail de test ( admin ) .
 *
 * <p>SRP : ne fait que ( a ) resoudre les destinataires effectifs depuis
 * la requete + le fallback {@link AlertsProperties} , ( b ) appeler
 * {@link EmailService#sendEmail} pour chaque destinataire , ( c ) mesurer
 * la duree et capturer les erreurs .
 *
 * <p>DRY : reutilise l'infra mail existante ( {@code JavaMailSender} via
 * {@link EmailService} ) - aucune duplication de logique SMTP / retry .
 *
 * <p>Permission : pas verifiee ici - delegue au controller via
 * {@code @PreAuthorize} ( separation auth / logique metier ) .
 */
@Slf4j
@Service
public class MailTestService {

    /** Valeurs par defaut servies par {@code GET /sample} pour pre-remplir l'IHM . */
    public static final String DEFAULT_SUBJECT = "[OpenADOM] Message de test administrateur";
    public static final String DEFAULT_BODY = """
            Ceci est un message de test envoyé depuis la console d'administration OpenADOM.

            Si vous le recevez, la configuration de la messagerie est opérationnelle :
            OpenADOM peut bien envoyer ses courriels ( notifications, demandes de droits,
            alertes ).

            Aucune action n'est requise. Ce message peut être supprimé.
            """;

    private final EmailService emailService;
    private final AlertsProperties alertsProperties;

    public MailTestService(EmailService emailService, AlertsProperties alertsProperties) {
        this.emailService = emailService;
        this.alertsProperties = alertsProperties;
    }

    /**
     * Resout la liste effective : ce que l'utilisateur a fourni si non
     * vide , sinon les destinataires configures dans {@link AlertsProperties} .
     */
    public List<String> resolveRecipients(List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested.stream().filter(s -> s != null && !s.isBlank()).toList();
        }
        return alertsProperties.getRecipientsList();
    }

    public MailTestRequest defaultSample() {
        return new MailTestRequest(alertsProperties.getRecipientsList(), DEFAULT_SUBJECT, DEFAULT_BODY);
    }

    /**
     * Envoie le mail aux destinataires resolus .
     *
     * @return resultat structure ( success / duree / erreur ) - jamais throws .
     */
    public MailTestResult send(MailTestRequest request) {
        List<String> recipients = resolveRecipients(request.recipients());
        long start = System.nanoTime();
        if (recipients.isEmpty()) {
            return MailTestResult.ko(recipients, durationMs(start),
                    "Aucun destinataire ( requete vide + OPENADOM_ALERT_RECIPIENTS non configure ) ");
        }
        String subject = request.subject() == null || request.subject().isBlank()
                ? DEFAULT_SUBJECT : request.subject();
        String body = request.body() == null ? "" : request.body();
        try {
            for (String to : recipients) {
                emailService.sendEmail("admin-test", to, subject, body);
            }
            long duration = durationMs(start);
            log.info("MailTest OK -> recipients={} subject=\"{}\" duration={}ms",
                    recipients, subject, duration);
            return MailTestResult.ok(recipients, duration);
        } catch (MailServiceUnavailableException | MailException e) {
            // Catches deliberement etroit : MailException = erreurs SMTP /
            // configuration ; MailServiceUnavailableException = wrapper interne
            // sur la chaine RETRY . Autres RuntimeException ( bug code ) sont
            // re-throw pour eviter de masquer une regression .
            long duration = durationMs(start);
            log.warn("MailTest KO -> recipients={} subject=\"{}\" duration={}ms error={}",
                    recipients, subject, duration, e.getMessage());
            return MailTestResult.ko(recipients, duration, e.getMessage());
        }
    }

    private static long durationMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
