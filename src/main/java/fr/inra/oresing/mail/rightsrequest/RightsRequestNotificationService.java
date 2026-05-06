package fr.inra.oresing.mail.rightsrequest;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Notifications envoyées lors du dépôt d'une demande de droits.
 * <p>
 * Phase 1 (#487) : à la création d'une demande, deux mails sont émis :
 * <ul>
 *     <li>au demandeur, pour confirmation que sa demande a bien été transmise ;</li>
 *     <li>aux gestionnaires (rôles {@code applicationManager} et {@code userManager})
 *         de l'application concernée uniquement, avec un lien direct vers la page
 *         de traitement de la demande.</li>
 * </ul>
 * <p>
 * Le périmètre des destinataires gestionnaires est strictement limité aux
 * membres directs des rôles Postgres scoppés à l'application
 * ({@code <appUuid>_applicationManager} et {@code <appUuid>_userManager}) :
 * les gestionnaires d'autres applications ne sont jamais notifiés.
 * <p>
 * L'envoi SMTP est délégué au pool {@code normalServiceExecutor} en
 * mode fire-and-forget : un échec d'envoi est journalisé mais ne fait
 * pas échouer la création de la demande.
 */
@Slf4j
@Service
public class RightsRequestNotificationService {

    /** Sujet du mail envoyé au demandeur. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> REQUESTER_SUBJECT = Map.of(
            Locale.FRENCH,  "OpenADOM - Votre demande d'accès a bien été transmise (%1$s)",
            Locale.ENGLISH, "OpenADOM - Your access request has been submitted (%1$s)"
    );

    /** Corps du mail envoyé au demandeur. Placeholders : %1$s = login, %2$s = nom localisé de l'application. */
    private static final Map<Locale, String> REQUESTER_BODY = Map.of(
            Locale.FRENCH, """
                    Bonjour %1$s,

                    Votre demande d'accès aux données de l'application "%2$s" a bien été transmise aux gestionnaires.
                    Vous serez notifié dès qu'elle sera traitée.

                    L'équipe OpenADOM""",
            Locale.ENGLISH, """
                    Hello %1$s,

                    Your data access request for application "%2$s" has been submitted to the managers.
                    You will be notified as soon as it is processed.

                    The OpenADOM team"""
    );

    /** Sujet du mail envoyé aux gestionnaires. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> MANAGERS_SUBJECT = Map.of(
            Locale.FRENCH,  "OpenADOM - Nouvelle demande d'accès à traiter (%1$s)",
            Locale.ENGLISH, "OpenADOM - New access request to handle (%1$s)"
    );

    /**
     * Corps du mail envoyé aux gestionnaires. Placeholders :
     * <ul>
     *     <li>%1$s = login du demandeur</li>
     *     <li>%2$s = email du demandeur</li>
     *     <li>%3$s = nom localisé de l'application</li>
     *     <li>%4$s = commentaire du demandeur</li>
     *     <li>%5$s = URL absolue de la page de traitement</li>
     * </ul>
     */
    private static final Map<Locale, String> MANAGERS_BODY = Map.of(
            Locale.FRENCH, """
                    Bonjour,

                    L'utilisateur %1$s (%2$s) a déposé une demande d'accès aux données de l'application "%3$s".

                    Détail de la demande :
                    %4$s

                    Pour traiter la demande, connectez-vous puis ouvrez :
                    %5$s

                    L'équipe OpenADOM""",
            Locale.ENGLISH, """
                    Hello,

                    User %1$s (%2$s) submitted a data access request for application "%3$s".

                    Request detail:
                    %4$s

                    To handle the request, sign in then open:
                    %5$s

                    The OpenADOM team"""
    );

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final Executor mailExecutor;

    @Value("${spring.mail.from}")
    private String mailFrom;

    @Value("${openadom.front.base-url}")
    private String frontBaseUrl;

    public RightsRequestNotificationService(
            final JavaMailSender mailSender,
            final UserRepository userRepository,
            final AuthenticationService authenticationService,
            @Qualifier("normalServiceExecutor") final Executor mailExecutor) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
        this.mailExecutor = mailExecutor;
    }

    /**
     * Notifie le demandeur ainsi que les gestionnaires de l'application qu'une
     * demande de droits vient d'être déposée. La résolution des destinataires
     * (lecture {@code pg_auth_members} + {@code oresiuser}) est faite de manière
     * synchrone sous rôle administrateur ; les envois SMTP sont délégués
     * au pool {@code normalServiceExecutor}.
     *
     * @param application    application cible de la demande (jamais {@code null})
     * @param requestId      identifiant de la demande qui vient d'être stockée
     * @param requester      utilisateur ayant déposé la demande
     * @param comment        commentaire libre saisi par le demandeur
     * @param requestLocale  locale de la requête HTTP (Accept-Language)
     */
    public void notifyRequestSubmitted(
            final Application application,
            final UUID requestId,
            final OreSiUser requester,
            final String comment,
            final Locale requestLocale) {

        if (application == null || requester == null || requestId == null) {
            log.warn("notifyRequestSubmitted ignored : application, requester or requestId is null");
            return;
        }

        final Locale locale = effectiveLocale(requestLocale);
        final String appLocalName = appDisplayName(application, locale);
        final String treatmentUrl = buildTreatmentUrl(application, requestId);

        final List<OreSiUser> managers = resolveManagers(application);

        // Envoi demandeur
        dispatch(() -> sendRequesterMail(requester, appLocalName, locale));

        // Envoi gestionnaires
        for (final OreSiUser manager : managers) {
            dispatch(() -> sendManagerMail(manager, requester, appLocalName, comment, treatmentUrl, locale));
        }
    }

    /**
     * Récupère les gestionnaires de l'application sous rôle administrateur,
     * puis restaure le rôle de l'utilisateur courant. Les doublons éventuels
     * (utilisateur à la fois {@code applicationManager} et {@code userManager})
     * sont éliminés.
     */
    private List<OreSiUser> resolveManagers(final Application application) {
        final String adminRoleName = OreSiRole.applicationManagerOf(application).getAsSqlRole();
        final String userManagerRoleName = OreSiRole.userManagerOf(application).getAsSqlRole();

        try {
            authenticationService.setRoleAdmin();
            final List<OreSiUser> applicationManagers = userRepository.findUsersGrantedRole(adminRoleName);
            final List<OreSiUser> userManagers = userRepository.findUsersGrantedRole(userManagerRoleName);
            // Déduplication par UUID en préservant l'ordre d'insertion (LinkedHashMap)
            final Map<UUID, OreSiUser> deduped = new LinkedHashMap<>();
            Stream.concat(applicationManagers.stream(), userManagers.stream())
                    .forEach(u -> deduped.putIfAbsent(u.getId(), u));
            return List.copyOf(deduped.values());
        } finally {
            authenticationService.setRoleForClient();
        }
    }

    private void sendRequesterMail(final OreSiUser requester, final String appLocalName, final Locale locale) {
        if (isBlank(requester.getEmail())) {
            log.warn("Skipping requester notification : email is empty for user {}", requester.getId());
            return;
        }
        final String subject = REQUESTER_SUBJECT.get(locale).formatted(appLocalName);
        final String body = REQUESTER_BODY.get(locale).formatted(requester.getLogin(), appLocalName);
        send(requester.getEmail(), subject, body);
    }

    private void sendManagerMail(
            final OreSiUser manager,
            final OreSiUser requester,
            final String appLocalName,
            final String comment,
            final String treatmentUrl,
            final Locale locale) {
        if (isBlank(manager.getEmail())) {
            log.warn("Skipping manager notification : email is empty for user {}", manager.getId());
            return;
        }
        final String subject = MANAGERS_SUBJECT.get(locale).formatted(appLocalName);
        final String body = MANAGERS_BODY.get(locale).formatted(
                requester.getLogin(),
                requester.getEmail() == null ? "" : requester.getEmail(),
                appLocalName,
                comment == null ? "" : comment,
                treatmentUrl
        );
        send(manager.getEmail(), subject, body);
    }

    private void send(final String to, final String subject, final String body) {
        final SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setFrom(mailFrom);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    /**
     * Soumet une tâche d'envoi au pool en isolant tout échec : un mail qui
     * tombe ne doit jamais faire échouer la création de la demande, ni
     * empêcher l'envoi des autres mails. Phase 1 = fire-and-forget, pas de retry.
     */
    private void dispatch(final Runnable task) {
        try {
            mailExecutor.execute(() -> {
                try {
                    task.run();
                } catch (final Exception e) {
                    log.error("Failed to send rights request notification mail : {}", e.toString(), e);
                }
            });
        } catch (final Exception e) {
            log.error("Failed to schedule rights request notification mail : {}", e.toString(), e);
        }
    }

    /**
     * Construit l'URL absolue vers la page de traitement de la demande.
     * Le slash final éventuel sur la base est supprimé pour éviter le double
     * slash dans l'URL générée.
     */
    private String buildTreatmentUrl(final Application application, final UUID requestId) {
        final String base = frontBaseUrl == null ? "" : frontBaseUrl.replaceAll("/+$", "");
        return "%s/applications/%s/authorizationsRequest/treatment/%s".formatted(
                base,
                application.getName(),
                requestId
        );
    }

    /**
     * Nom affichable de l'application : on tente la version localisée et on
     * retombe sur le nom technique si la locale ne fournit aucun libellé ou
     * si la configuration de l'application n'est pas chargée.
     */
    private String appDisplayName(final Application application, final Locale locale) {
        if (application.getConfiguration() != null) {
            try {
                final String localized = application.getLocalizedLocalName(locale);
                if (localized != null && !localized.isBlank()) {
                    return localized;
                }
            } catch (final Exception e) {
                log.warn("Localized application name unavailable for {} : {}", application.getName(), e.toString());
            }
        }
        return application.getName();
    }

    /** Restreint la locale aux deux langues supportées par les templates (FR par défaut). */
    private Locale effectiveLocale(final Locale locale) {
        return locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage())
                ? Locale.ENGLISH
                : Locale.FRENCH;
    }

    private boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
