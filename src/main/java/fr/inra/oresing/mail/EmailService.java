package fr.inra.oresing.mail;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.MailServiceUnavailableException;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static fr.inra.oresing.mail.EmailService.UPLOAD_STATE.UNPUBLISHED;

@Service
@Slf4j
public class EmailService implements Email {
    public static final String OPENADOM_INRAE_FR = "openadom@inrae.fr";
    private static final String NEW_ACCOUNT_SUBJECT = "Création de compte / Account creation";
    private static final String NEW_ACCOUNT_FR = "Vous venez de créer un compte sur l'application OPENAdom. %n" +
                                                 "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.%n\n";
    private static final String NEW_ACCOUNT_EN = "You have just created an account on the OPENAdomoresie application. %n" +
                                                 "To validate your e-mail, enter the validation key when connecting.%n\n";
    private static final String EMAIL_CHANGED_SUBJECT = "Validation email / Email validation";
    private static final String EMAIL_CHANGED_FR = "Vous venez de modifier votre email. \n" +
                                                   "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.";
    private static final String EMAIL_CHANGED_EN = "You have just changed your email. \n" +
                                                   "To validate your e-mail, enter the validation key when connecting.";
    private static final String VALIDATION_KEY_SUBJECT = "Clef de validation / Validation key";
    private static final Map<UPLOAD_STATE, Map<Locale, String>> SUCCESS_UPLOAD_SUBJECTS = Map.ofEntries(
            Map.entry(UNPUBLISHED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été dépublié",
                    Locale.ENGLISH, "Your file has been unpublished"
            )),
            Map.entry(UPLOAD_STATE.PUBLISHED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été publié",
                    Locale.ENGLISH, "Your file has been published"
            )),
            Map.entry(UPLOAD_STATE.UPLOADED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été enregistré",
                    Locale.ENGLISH, "Your file has been registered"
            )),
            Map.entry(UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été supprimé",
                    Locale.ENGLISH, "Your file has been deleted"
            )),
            Map.entry(UPLOAD_STATE.PUBLISH_STARTED, Map.of(
                    Locale.FRENCH, "Publication de votre fichier en cours",
                    Locale.ENGLISH, "Publication of your file in progress"
            )),
            Map.entry(UPLOAD_STATE.UNPUBLISH_STARTED, Map.of(
                    Locale.FRENCH, "Dépublication de votre fichier en cours",
                    Locale.ENGLISH, "Unpublication of your file in progress"
            )),
            Map.entry(UPLOAD_STATE.DELETE_STARTED, Map.of(
                    Locale.FRENCH, "Suppression de votre fichier en cours",
                    Locale.ENGLISH, "Deletion of your file in progress"
            ))
    );
    private static final Map<UPLOAD_STATE, Map<Locale, String>> SUCCESS_UPLOAD_TEXTS = Map.ofEntries(
            Map.entry(UNPUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été dépublié pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully unpublished for the application %2$s.
                            %1$s contains %3$s record(s)"""
            )),
            Map.entry(UPLOAD_STATE.PUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été publié pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully published for the application %2$s.
                            %1$s contains %3$s record(s)"""
            )),
            Map.entry(UPLOAD_STATE.UPLOADED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été enregistré pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully registered for the application %2$s.
                            %1$s contains %3$s record(s)"""
            )),
            Map.entry(UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été supprimé pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully deleted for the application %2$s.
                            %1$s contains %3$s record(s)"""
            )),
            Map.entry(UPLOAD_STATE.PUBLISH_STARTED, Map.of(
                    Locale.FRENCH, """
                            La publication du fichier de données "%1$s" pour l'application %2$s est en cours.
                            Vous recevrez un nouveau message dès que l'opération sera terminée.""",
                    Locale.ENGLISH, """
                            Publication of the data file "%1$s" for the application %2$s is in progress.
                            You will receive a new message as soon as the operation is complete."""
            )),
            Map.entry(UPLOAD_STATE.UNPUBLISH_STARTED, Map.of(
                    Locale.FRENCH, """
                            La dépublication du fichier de données "%1$s" pour l'application %2$s est en cours.
                            Vous recevrez un nouveau message dès que l'opération sera terminée.""",
                    Locale.ENGLISH, """
                            Unpublication of the data file "%1$s" for the application %2$s is in progress.
                            You will receive a new message as soon as the operation is complete."""
            )),
            Map.entry(UPLOAD_STATE.DELETE_STARTED, Map.of(
                    Locale.FRENCH, """
                            La suppression du fichier de données "%1$s" pour l'application %2$s est en cours.
                            Vous recevrez un nouveau message dès que l'opération sera terminée.""",
                    Locale.ENGLISH, """
                            Deletion of the data file "%1$s" for the application %2$s is in progress.
                            You will receive a new message as soon as the operation is complete."""
            ))
    );

    /**
     * #477 - Corps des emails de succès pour l'état UPLOADED ( insertion ) ,
     * différencié selon qu'il s'agit d'un référentiel ( TRUE ) ou d'un type
     * de données ( FALSE ). Ajout du nom du fichier CSV soumis en plus du
     * titre localisé , conformément aux propositions d'évolution du document
     * ODT joint à l'issue. Placeholders :
     *   %1$s = titre ( référentiel ou type de données , localisé )
     *   %2$s = titre de l'application ( localisé )
     *   %3$s = nombre d'enregistrements
     *   %4$s = nom du fichier CSV soumis
     */
    private static final Map<Boolean, Map<Locale, String>> INSERT_SUCCESS_BODIES = Map.of(
            Boolean.TRUE, Map.of(
                    Locale.FRENCH, """
                            Votre fichier de données "%4$s" du référentiel "%1$s" a bien été enregistré dans l'application %2$s.
                            Le référentiel "%1$s" contient %3$s enregistrement(s).""",
                    Locale.ENGLISH, """
                            Your data file "%4$s" from the reference "%1$s" has been successfully registered in the application %2$s.
                            The reference "%1$s" contains %3$s record(s)"""
            ),
            Boolean.FALSE, Map.of(
                    Locale.FRENCH, """
                            Votre fichier de données "%4$s" du type de données "%1$s" a bien été enregistré dans l'application %2$s.
                            Le type de données "%1$s" contient %3$s enregistrement(s).""",
                    Locale.ENGLISH, """
                            Your data file "%4$s" from the data type "%1$s" has been successfully registered in the application %2$s.
                            The data type "%1$s" contains %3$s record(s)"""
            )
    );

    /**
     * #477 - Sujets des emails d'erreur d'insertion , différenciés selon
     * qu'il s'agit d'un référentiel ( TRUE ) ou d'un type de données ( FALSE ).
     * Placeholders :
     *   %1$s = titre ( localisé )
     *   %2$s = titre de l'application ( localisé )
     */
    private static final Map<Boolean, Map<Locale, String>> ERROR_SUBJECTS = Map.of(
            Boolean.TRUE, Map.of(
                    Locale.FRENCH, "Erreur lors de l'opération sur le référentiel %1$s de l'application %2$s",
                    Locale.ENGLISH, "Error during the operation on the reference dataset %1$s of the application %2$s"
            ),
            Boolean.FALSE, Map.of(
                    Locale.FRENCH, "Erreur lors de l'opération sur le type de données %1$s de l'application %2$s",
                    // #477 - "reference dataset" conservé tel quel , conformément au document ODT
                    // ( remonté dans le commentaire de l'issue pour clarification ultérieure ).
                    Locale.ENGLISH, "Error during the operation on the reference dataset %1$s of the application %2$s"
            )
    );

    /**
     * #477 - Corps des emails d'erreur d'insertion , différenciés selon
     * qu'il s'agit d'un référentiel ( TRUE ) ou d'un type de données ( FALSE ).
     * Le texte d'accompagnement encadre le message technique brut pour
     * guider l'utilisateur vers une reprise ou un contact administrateur.
     * Le nom du fichier CSV en erreur est désormais inclus , en cohérence
     * avec l'email de succès ( harmonisation proposée en complément du
     * document ODT ).
     * Placeholders :
     *   %1$s = titre ( localisé )
     *   %2$s = titre de l'application ( localisé )
     *   %3$s = message d'erreur technique brut ( JSON , tel que renvoyé par le backend )
     *   %4$s = nom du fichier CSV soumis
     */
    private static final Map<Boolean, Map<Locale, String>> ERROR_BODIES = Map.of(
            Boolean.TRUE, Map.of(
                    Locale.FRENCH, """
                            Une erreur est survenue lors du traitement du fichier de données "%4$s" du référentiel "%1$s" de l'application %2$s.
                            Vous trouverez ci-dessous le message d'erreur technique associé :

                            %3$s

                            Vous pouvez vérifier vos données et réessayer.
                            Si le problème persiste, nous vous invitons à contacter le(s) administrateur(s) du système d'information.""",
                    Locale.ENGLISH, """
                            An error occurred while processing the data file "%4$s" from the reference "%1$s" in the application %2$s.
                            The technical error message is provided below:

                            %3$s

                            You may review your data and try again.
                            If the issue persists, please contact the information system administrator(s)."""
            ),
            Boolean.FALSE, Map.of(
                    Locale.FRENCH, """
                            Une erreur est survenue lors du traitement du fichier de données "%4$s" du type de données "%1$s" de l'application %2$s.
                            Vous trouverez ci-dessous le message d'erreur technique associé :

                            %3$s

                            Vous pouvez vérifier vos données et réessayer.
                            Si le problème persiste, nous vous invitons à contacter le(s) administrateur(s) du système d'information.""",
                    Locale.ENGLISH, """
                            An error occurred while processing the data file "%4$s" from the data type "%1$s" in the application %2$s.
                            The technical error message is provided below:

                            %3$s

                            You may review your data and try again.
                            If the issue persists, please contact the information system administrator(s)."""
            )
    );

    // Mise en forme du corps du mail bilingue ( FR + EN ) :
    //   {title_fr}Votre clé de validation est :
    //   {key}
    //                              <- ligne vide
    //   {title_en}Your validation key is:
    //   {key}
    //                              <- ligne vide ( pour ne pas coller au bloc
    //                                  "L'équipe d'OpenAdom" ajouté par
    //                                  MAIL_MESSAGE_TEMPLATE )
    //
    // Pour le cas VALIDATION_KEY ( forgot-password ) où title_fr et
    // title_en sont vides , le rendu obtenu est exactement :
    //   Bonjour {login}
    //                              <- ligne vide ( de MAIL_MESSAGE_TEMPLATE )
    //   Votre clé de validation est :
    //   {key}
    //                              <- ligne vide
    //   Your validation key is:
    //   {key}
    //                              <- ligne vide
    //   L'équipe d'OpenAdom
    //
    // Évite le bug d'espacement excessif observé image #111 ( double saut
    // de ligne entre "Bonjour" et "Votre clé" car le précédent template
    // commençait par %2$s%n%n qui combiné avec le %n%n de MESSAGE_TEMPLATE
    // produisait 4 sauts de ligne = 3 lignes vides ) .
    //
    // Pour les cas NEW_ACCOUNT / NEW_EMAIL où title_fr / title_en
    // contiennent du texte d'introduction se terminant déjà par leur
    // propre %n%n , le rendu reste cohérent : le titre s'affiche avant
    // la clé , séparé par une ligne vide .
    private static final String MAIL_VERIFICATION_TEMPLATE =
            "%2$sVotre clé de validation est :%n%1$s%n%n"
            + "%3$sYour validation key is:%n%1$s%n";
    private static final String MAIL_MESSAGE_TEMPLATE =
            "Bonjour %1$s%n%n" +
            "%2$s%n" +
            "L'équipe d'OpenAdom";
    /**
     * Pattern d'invalidation des headers SMTP : tout CR ou LF dans un
     * champ ( Subject , From , recipient ) ouvre une injection RFC 5322
     * permettant a un attaquant d'ajouter des entetes arbitraires ( BCC ,
     * Reply-To ) si la valeur provient d'une saisie utilisateur ( login ,
     * nom de fichier , nom de datatype ) . On strip ces caracteres
     * avant insertion dans le message .
     */
    private static final Pattern HEADER_INJECTION_CHARS = Pattern.compile("[\\r\\n]");

    private final JavaMailSender mailSender;
    private final ServiceContainer serviceContainer;
    private final LocaleResolver localeResolver;
    /**
     * Adresse d'expediteur applicative , configurable via
     * {@code spring.mail.from} . Defaut prod = {@value #OPENADOM_INRAE_FR}
     * pour rester compatible si la propriete n'est pas explicitement
     * fixee dans l'environnement ( evite NPE silencieuse a runtime ) .
     */
    private final String mailFrom;

    @Autowired
    public EmailService(
            final JavaMailSender mailSender,
            final LocaleResolver localeResolver,
            final ServiceContainer serviceContainer,
            @Value("${spring.mail.from:" + OPENADOM_INRAE_FR + "}") final String mailFrom) {
        this.mailSender = mailSender;
        this.localeResolver = localeResolver;
        this.serviceContainer = serviceContainer;
        this.mailFrom = mailFrom;
    }

    /**
     * Centralise tous les appels {@link JavaMailSender#send} pour :
     * <ol>
     *   <li>uniformiser la traduction d'une defaillance SMTP en
     *       {@link MailServiceUnavailableException} ( runtime , traduite en
     *       HTTP 503 par OreExceptionHandler ) ;</li>
     *   <li>tenter UN seul retry automatique <strong>immediat</strong> en
     *       cas d'echec initial - couvre les hoquets transients .</li>
     * </ol>
     *
     * <p><b>Borne stricte temps</b> : chaque tentative est limitee par les
     * timeouts SMTP {@code mail.smtp.connectiontimeout|timeout|writetimeout}
     * ( configurables via {@code OPENADOM_MAIL_SEND_TIMEOUT_MS} , default
     * 3000 ms ) . Total worst-case : 2 x timeout = ~6s avant que le caller
     * recoive l'exception . Pas de sleep entre tentatives : un timeout SMTP
     * signifie deja attente cote socket , inutile d'attendre en plus .
     *
     * <p><b>Garantie "once and only once"</b> : best-effort . Le retry n'est
     * declenche que si la tentative initiale a leve une {@link MailException} ,
     * qui dans la quasi-totalite des cas signifie que le SMTP n'a PAS accepte
     * le message ( auth refused , connection refused , relay 4xx/5xx ,
     * socket timeout avant ack ) . Le seul cas pathologique ou un
     * double-envoi est possible est un timeout apres que le SMTP a accepte
     * le message mais avant le ACK reseau - extremement rare avec un relay
     * configure correctement . Risque juge acceptable .
     *
     * <p>Le service appelant doit considerer qu'aucune mutation downstream
     * dependante de l'envoi du mail ne doit etre persistee tant que cette
     * methode n'a pas reussi ( strategie atomique : "send first , persist
     * after" - cf flow updateAccount / requestEmailChange ) .
     */
    /**
     * Pre-validation des champs du message AVANT l'appel a la pile JavaMail .
     * Sans cette garde , une donnee invalide ( recipient null / vide ,
     * subject null , from null ) leve une {@link IllegalStateException}
     * dans {@code JavaMailSenderImpl} qui n'est PAS un {@link MailException} -
     * elle echappe au catch ci-dessous et remonte en 500 au lieu de 503 .
     * On verifie ici fail-fast pour eviter ce bug de classification d'erreur .
     */
    private void validateMessage(final SimpleMailMessage message) {
        if (message.getTo() == null || message.getTo().length == 0) {
            throw new MailServiceUnavailableException("Mail recipient ( To ) is missing - refusing to send", null);
        }
        for (final String to : message.getTo()) {
            if (to == null || to.isBlank()) {
                throw new MailServiceUnavailableException("Mail recipient ( To ) contains a null or blank entry", null);
            }
        }
        if (message.getFrom() == null || message.getFrom().isBlank()) {
            throw new MailServiceUnavailableException("Mail sender ( From ) is missing - check spring.mail.from", null);
        }
        if (message.getSubject() == null) {
            throw new MailServiceUnavailableException("Mail subject is missing", null);
        }
    }

    /**
     * Sanitize une valeur destinee a etre injectee dans un header SMTP
     * ( Subject , From , To ) . Supprime les CR/LF qui pourraient permettre
     * a un appelant malveillant ( ou une donnee utilisateur non controlee )
     * d'ajouter des entetes arbitraires - cf RFC 5322 § 2.2 .
     *
     * @return la valeur d'origine si elle ne contient pas de CR/LF , sinon
     *         une copie nettoyee . {@code null} en entree -> {@code null} en
     *         sortie ( les appelants restent responsables des null guards ) .
     */
    private static String sanitizeHeader(final String value) {
        if (value == null) return null;
        if (!HEADER_INJECTION_CHARS.matcher(value).find()) return value;
        return HEADER_INJECTION_CHARS.matcher(value).replaceAll(" ");
    }

    /**
     * Centralise tous les appels {@link JavaMailSender#send} pour :
     * <ol>
     *   <li>uniformiser la traduction d'une defaillance SMTP en
     *       {@link MailServiceUnavailableException} ( runtime , traduite en
     *       HTTP 503 par OreExceptionHandler ) ;</li>
     *   <li>tenter UN seul retry automatique <strong>immediat</strong> en
     *       cas d'echec initial - couvre les hoquets transients ;</li>
     *   <li>valider fail-fast les champs minimaux ( cf {@link #validateMessage} )
     *       pour eviter qu'une erreur de programmation soit classifiee 500 .</li>
     * </ol>
     *
     * <p><b>Trace</b> : un INFO est emis sur succes ( recipient + subject ) ,
     * pour permettre de tracer exactement ce qui est parti vers le SMTP .
     * Indispensable au diagnostic "j'ai pas recu de mail" - sans ce log on
     * ne sait pas si on a meme tente l'envoi .
     *
     * <p><b>Borne temps</b> : chaque tentative est limitee par les timeouts
     * SMTP {@code mail.smtp.connectiontimeout|timeout|writetimeout}
     * ( configurables via {@code OPENADOM_MAIL_SEND_TIMEOUT_MS} , default
     * 3000 ms ) . Total worst-case : 2 x timeout = ~6s avant que le caller
     * recoive l'exception . Pas de sleep entre tentatives .
     *
     * <p><b>Garantie "once and only once"</b> : best-effort . Le retry n'est
     * declenche que si la tentative initiale a leve une {@link MailException} ,
     * qui dans la quasi-totalite des cas signifie que le SMTP n'a PAS accepte
     * le message . Cas pathologique extreme : timeout apres ack SMTP mais
     * avant ack reseau -> double-envoi rare , risque juge acceptable .
     *
     * <p>Le service appelant doit considerer qu'aucune mutation downstream
     * dependante de l'envoi du mail ne doit etre persistee tant que cette
     * methode n'a pas reussi ( strategie atomique : "send first , persist
     * after" - cf flow updateAccount / requestEmailChange ) .
     */
    private void sendOrThrow(final SimpleMailMessage message) {
        validateMessage(message);
        final String recipients = java.util.Arrays.toString(message.getTo());
        final String subject = message.getSubject();
        try {
            mailSender.send(message);
            log.info("Mail send OK -> to={} subject=\"{}\" from={}", recipients, subject, message.getFrom());
            return;
        } catch (final MailException firstAttempt) {
            log.warn("Mail send attempt 1/2 failed -> to={} subject=\"{}\" cause={}",
                    recipients, subject, firstAttempt.getMessage());
            try {
                mailSender.send(message);
                log.info("Mail send attempt 2/2 OK after initial failure -> to={} subject=\"{}\"",
                        recipients, subject);
            } catch (final MailException secondAttempt) {
                log.error("Mail send FAILED twice -> to={} subject=\"{}\" - giving up",
                        recipients, subject, secondAttempt);
                throw new MailServiceUnavailableException(
                        "SMTP send failed twice ( initial + 1 immediate retry ) for recipient(s) " + recipients,
                        secondAttempt);
            }
        }
    }

    @Override
    public void sendEmail(final String login, final String to, final String subject, final String message) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(sanitizeHeader(to));
        mailMessage.setFrom(sanitizeHeader(mailFrom));
        mailMessage.setSubject(sanitizeHeader(subject));
        mailMessage.setText(String.format(MAIL_MESSAGE_TEMPLATE, login, message));
        sendOrThrow(mailMessage);
    }


    @Override
    public void sendEmailValidation(final String login, final String email, final String verificationKey, final MESSAGES messages) {
        String message = String.format(MAIL_VERIFICATION_TEMPLATE, verificationKey, messages.title_fr, messages.title_en);
        sendEmail(login, email, messages.subject, message);
    }

    @Override
    public void sendUploadZipEmail(
            final String to,
            final String subject,
            final String message,
            final String downloadUrl,
            FileSenderInternationalisation fileSenderInternationalisation,
            String internationnalizedDataName) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(sanitizeHeader(to));
        mailMessage.setFrom(sanitizeHeader(mailFrom));
        mailMessage.setSubject(sanitizeHeader(subject));
        mailMessage.setText(
                String.format(
                        fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID),
                        internationnalizedDataName
                )
        );
        sendOrThrow(mailMessage);
    }

    @Override
    public void sendUpoadSuccessMail(Application application, String dataName, String fileName, UPLOAD_STATE uploadState, Locale locale, DataVersioningResult dataVersioningResult, OreSiUser currentUser) {
        long count = dataVersioningResult.dataSynthesis().stream()
                .filter(dataSynthesis -> dataSynthesis.getReferenceType().equals(dataName))
                .findFirst()
                .map(ApplicationResult.DataSynthesis::getLineCount)
                .orElse(0);

        String localizedApplicationName = application.getLocalizedLocalName(locale);
        String localizedDataName = application.getLocalizedDataName(locale, dataName);
        localizedDataName = localizedDataName == null ? dataName : localizedDataName;
        Locale effectiveLocale = locale.equals(Locale.ENGLISH) ? Locale.ENGLISH : Locale.FRENCH;
        String subject = SUCCESS_UPLOAD_SUBJECTS.get(uploadState).get(effectiveLocale);

        // #477 - Pour l'insertion ( UPLOADED ) , le template évolue : il
        // distingue référentiel / type de données et inclut le nom du fichier
        // CSV soumis ( document ODT joint à l'issue ). Les autres états
        // ( publication , dépublication , suppression ) restent sur le
        // template existant car hors scope explicite du document.
        final String text;
        if (uploadState == UPLOAD_STATE.UPLOADED) {
            boolean isReference = !application.isData(dataName);
            String safeFileName = fileName == null ? "" : fileName;
            text = INSERT_SUCCESS_BODIES.get(isReference).get(effectiveLocale)
                    .formatted(localizedDataName, localizedApplicationName, count, safeFileName);
        } else {
            text = SUCCESS_UPLOAD_TEXTS.get(uploadState).get(effectiveLocale)
                    .formatted(localizedDataName, localizedApplicationName, count);
        }

        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(sanitizeHeader(currentUser.getEmail()));
        mailMessage.setFrom(sanitizeHeader(mailFrom));
        mailMessage.setSubject(sanitizeHeader(subject));
        mailMessage.setText(text);

        sendOrThrow(mailMessage);
    }

    @Override
    public void sendUpoadErrorsMail(Locale locale, String application, String dataName, String fileName, boolean isReference, OreSiUser currentUser, String body) {
        // #477 - Le sujet et le corps sont désormais différenciés selon
        // qu'il s'agit d'un référentiel ou d'un type de données , et le
        // corps encadre le message technique brut par un texte
        // d'accompagnement ( document ODT joint à l'issue ). Le nom du
        // fichier CSV en erreur est inclus par harmonisation avec l'email
        // de succès.
        Locale effectiveLocale = Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
        String safeFileName = fileName == null ? "" : fileName;
        String subject = ERROR_SUBJECTS.get(isReference).get(effectiveLocale)
                .formatted(dataName, application);
        String text = ERROR_BODIES.get(isReference).get(effectiveLocale)
                .formatted(dataName, application, body, safeFileName);

        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(sanitizeHeader(currentUser.getEmail()));
        mailMessage.setFrom(sanitizeHeader(mailFrom));
        mailMessage.setSubject(sanitizeHeader(subject));
        mailMessage.setText(text);

        sendOrThrow(mailMessage);
    }

    public enum UPLOAD_STATE {
        // Etats terminaux ( phase 2 END ) :
        UPLOADED,
        PUBLISHED,
        UNPUBLISHED,
        DELETED,
        // Etats demarrage ( phase 1 START ) - notification immediate post-COMMIT
        // de la phase 1 , utilisateur sait que son action est prise en compte
        // avant la fin du traitement async ( phase 2 ) :
        PUBLISH_STARTED,
        UNPUBLISH_STARTED,
        DELETE_STARTED
    }

    public enum MESSAGES {

        NEW_ACCOUNT(NEW_ACCOUNT_SUBJECT, NEW_ACCOUNT_FR, NEW_ACCOUNT_EN),
        NEW_EMAIL(EMAIL_CHANGED_SUBJECT, EMAIL_CHANGED_FR, EMAIL_CHANGED_EN),
        VALIDATION_KEY(VALIDATION_KEY_SUBJECT, "", "");

        final String subject;
        final String title_fr;
        final String title_en;

        MESSAGES(final String subject, final String title_fr, final String title_en) {
            this.subject = subject;
            this.title_fr = title_fr;
            this.title_en = title_en;
        }
    }

}