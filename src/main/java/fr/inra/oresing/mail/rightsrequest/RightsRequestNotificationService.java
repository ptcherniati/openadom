package fr.inra.oresing.mail.rightsrequest;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.rightsrequest.TreatmentDecision;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.*;
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
            Locale.FRENCH, "OpenADOM - Votre demande d'accès a bien été transmise (%1$s)",
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
            Locale.FRENCH, "OpenADOM - Nouvelle demande d'accès à traiter (%1$s)",
            Locale.ENGLISH, "OpenADOM - New access request to handle (%1$s)"
    );

    /** Sujet du mail envoyé au demandeur lorsque sa demande est approuvée. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> TREATED_APPROVED_SUBJECT = Map.of(
            Locale.FRENCH, "OpenADOM - Votre demande d'accès a été acceptée (%1$s)",
            Locale.ENGLISH, "OpenADOM - Your access request has been approved (%1$s)"
    );

    /** Sujet du mail envoyé au demandeur lorsque sa demande est refusée. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> TREATED_REJECTED_SUBJECT = Map.of(
            Locale.FRENCH, "OpenADOM - Votre demande d'accès a été refusée (%1$s)",
            Locale.ENGLISH, "OpenADOM - Your access request has been rejected (%1$s)"
    );

    /** Sujet du mail envoyé aux gestionnaires lors d'une approbation. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> MANAGERS_TREATMENT_APPROVED_SUBJECT = Map.of(
            Locale.FRENCH, "OpenADOM - Une demande d'accès a été approuvée (%1$s)",
            Locale.ENGLISH, "OpenADOM - An access request has been approved (%1$s)"
    );

    /** Sujet du mail envoyé aux gestionnaires lors d'un refus. Placeholder : %1$s = nom localisé de l'application. */
    private static final Map<Locale, String> MANAGERS_TREATMENT_REJECTED_SUBJECT = Map.of(
            Locale.FRENCH, "OpenADOM - Une demande d'accès a été refusée (%1$s)",
            Locale.ENGLISH, "OpenADOM - An access request has been rejected (%1$s)"
    );

    /**
     * Corps du mail envoyé aux gestionnaires lors du traitement d'une demande.
     * Placeholders communs aux variantes APPROVED / REJECTED :
     * <ul>
     *     <li>%1$s = login du demandeur</li>
     *     <li>%2$s = email du demandeur</li>
     *     <li>%3$s = nom localisé de l'application</li>
     *     <li>%4$s = login du gestionnaire qui a traité la demande</li>
     * </ul>
     */
    private static final Map<Locale, String> MANAGERS_TREATMENT_APPROVED_BODY = Map.of(
            Locale.FRENCH, """
                    Bonjour,
                    
                    La demande d'accès aux données de l'utilisateur %1$s (%2$s) sur l'application "%3$s" a été APPROUVÉE par %4$s.
                    
                    L'équipe OpenADOM""",
            Locale.ENGLISH, """
                    Hello,
                    
                    The data access request from user %1$s (%2$s) on application "%3$s" has been APPROVED by %4$s.
                    
                    The OpenADOM team"""
    );

    private static final Map<Locale, String> MANAGERS_TREATMENT_REJECTED_BODY = Map.of(
            Locale.FRENCH, """
                    Bonjour,
                    
                    La demande d'accès aux données de l'utilisateur %1$s (%2$s) sur l'application "%3$s" a été REFUSÉE par %4$s.
                    
                    L'équipe OpenADOM""",
            Locale.ENGLISH, """
                    Hello,
                    
                    The data access request from user %1$s (%2$s) on application "%3$s" has been REJECTED by %4$s.
                    
                    The OpenADOM team"""
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

    /**
     * Notifie le demandeur du traitement de sa demande de droits ( #487 Phase 3 ).
     *
     * <p>Le sujet du mail est généré côté serveur ( internationalisé selon
     * la décision et la locale ). Le corps est celui composé par le
     * gestionnaire dans la page de traitement, transmis tel quel au
     * destinataire ( y compris ses éventuels retours à la ligne ).</p>
     *
     * <p>Aucun mail n'est émis si :
     * <ul>
     *   <li>{@code suppressMail} vaut {@code true} ( le gestionnaire a coché
     *       "Ne pas envoyer d'email à l'utilisateur" ) ;</li>
     *   <li>le corps du mail est vide ;</li>
     *   <li>l'adresse email du demandeur est manquante.</li>
     * </ul>
     * Dans tous les cas, l'envoi est délégué au pool {@code normalServiceExecutor}
     * en mode fire-and-forget : un échec est journalisé mais ne fait pas
     * échouer la validation du traitement.</p>
     *
     * @param application   application cible de la demande
     * @param requester     destinataire ( demandeur initial )
     * @param treatedBy     gestionnaire ayant traité la demande ( utilisé dans
     *                      le mail aux autres gestionnaires pour traçabilité )
     * @param decision      décision du gestionnaire ( APPROVED / REJECTED )
     * @param mailSubject   sujet libre saisi par le gestionnaire ( si non vide,
     *                      remplace le sujet i18n par défaut )
     * @param mailBody      corps libre saisi par le gestionnaire ( utilisé tel
     *                      quel pour le mail au demandeur ; ignoré pour le
     *                      mail aux gestionnaires qui suit son propre template )
     * @param suppressMail  désactive uniquement l'envoi du mail au demandeur
     *                      ( les gestionnaires sont toujours notifiés pour
     *                      la traçabilité interne )
     * @param requestLocale locale de la requête HTTP courante
     */
    public void notifyRequestTreated(
            final Application application,
            final OreSiUser requester,
            final OreSiUser treatedBy,
            final TreatmentDecision decision,
            final String mailSubject,
            final String mailBody,
            final boolean suppressMail,
            final Locale requestLocale) {

        if (application == null || decision == null) {
            log.warn("notifyRequestTreated ignored : application or decision is null");
            return;
        }

        final Locale locale = effectiveLocale(requestLocale);
        final String appLocalName = appDisplayName(application, locale);

        // 1. Notification du demandeur ( contenu = texte saisi par le
        //    gestionnaire dans la page de traitement ). Suppressible.
        notifyRequesterOfTreatment(requester, decision, appLocalName, mailSubject, mailBody, suppressMail, locale);

        // 2. Notification de tous les gestionnaires de l'application
        //    ( applicationManager + userManager strictement scoppés ) avec
        //    un template d'audit. Toujours envoyé , même si la demande a
        //    été traitée sans mail au demandeur.
        notifyManagersOfTreatment(application, requester, treatedBy, decision, appLocalName, locale);
    }

    private void notifyRequesterOfTreatment(
            final OreSiUser requester,
            final TreatmentDecision decision,
            final String appLocalName,
            final String mailSubject,
            final String mailBody,
            final boolean suppressMail,
            final Locale locale) {
        if (suppressMail) {
            log.info("Treatment mail to requester suppressed by manager (suppressMail=true)");
            return;
        }
        if (mailBody == null || mailBody.isBlank()) {
            log.info("Treatment mail body is empty : no mail sent to requester");
            return;
        }
        if (requester == null || isBlank(requester.getEmail())) {
            log.warn("Cannot send treatment mail : requester or email is empty");
            return;
        }
        // Sujet : on privilégie celui saisi par le gestionnaire ; on retombe
        // sur le template i18n par défaut s'il est vide.
        final String subject;
        if (mailSubject != null && !mailSubject.isBlank()) {
            subject = mailSubject;
        } else {
            final Map<Locale, String> subjectMap = decision == TreatmentDecision.REJECTED
                    ? TREATED_REJECTED_SUBJECT
                    : TREATED_APPROVED_SUBJECT;
            subject = subjectMap.get(locale).formatted(appLocalName);
        }
        dispatch(() -> send(requester.getEmail(), subject, mailBody));
    }

    private void notifyManagersOfTreatment(
            final Application application,
            final OreSiUser requester,
            final OreSiUser treatedBy,
            final TreatmentDecision decision,
            final String appLocalName,
            final Locale locale) {
        final List<OreSiUser> managers = resolveManagers(application);
        if (managers.isEmpty()) {
            log.info("No managers to notify of treatment for application {}", application.getName());
            return;
        }
        final Map<Locale, String> subjectMap = decision == TreatmentDecision.REJECTED
                ? MANAGERS_TREATMENT_REJECTED_SUBJECT
                : MANAGERS_TREATMENT_APPROVED_SUBJECT;
        final Map<Locale, String> bodyMap = decision == TreatmentDecision.REJECTED
                ? MANAGERS_TREATMENT_REJECTED_BODY
                : MANAGERS_TREATMENT_APPROVED_BODY;
        final String subject = subjectMap.get(locale).formatted(appLocalName);
        final String requesterLogin = requester != null ? requester.getLogin() : "?";
        final String requesterEmail = requester != null && requester.getEmail() != null
                ? requester.getEmail() : "";
        final String treatedByLogin = treatedBy != null ? treatedBy.getLogin() : "?";
        final String body = bodyMap.get(locale).formatted(
                requesterLogin, requesterEmail, appLocalName, treatedByLogin);
        for (final OreSiUser manager : managers) {
            if (isBlank(manager.getEmail())) {
                log.info("Skipping manager treatment mail : email empty for {}", manager.getId());
                continue;
            }
            dispatch(() -> send(manager.getEmail(), subject, body));
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
        final String base = StringUtils.removeEnd(frontBaseUrl, "/");
        ;
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