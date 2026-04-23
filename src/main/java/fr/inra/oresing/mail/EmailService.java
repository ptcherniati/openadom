package fr.inra.oresing.mail;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;
import java.util.Map;

import static fr.inra.oresing.mail.EmailService.UPLOAD_STATE.UNPUBLISHED;

@Service
@RequiredArgsConstructor
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
    private static final Map<UPLOAD_STATE, Map<Locale, String>> SUCCESS_UPLOAD_SUBJECTS = Map.of(
            UNPUBLISHED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été dépublié",
                    Locale.ENGLISH, "Your file has been unpublished"
            ),
            UPLOAD_STATE.PUBLISHED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été publié",
                    Locale.ENGLISH, "Your file has been published"
            ),
            UPLOAD_STATE.UPLOADED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été enregistré",
                    Locale.ENGLISH, "Your file has been registered"
            ),
            UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été supprimé",
                    Locale.ENGLISH, "Your file has been deleted"
            )
    );
    private static final Map<UPLOAD_STATE, Map<Locale, String>> SUCCESS_UPLOAD_TEXTS = Map.of(
            UNPUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été dépublié pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully unpublished for the application %2$s.
                            %1$s contains %3$s record(s)"""
            ),
            UPLOAD_STATE.PUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été publié pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully published for the application %2$s.
                            %1$s contains %3$s record(s)"""
            ),
            UPLOAD_STATE.UPLOADED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été enregistré pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully registered for the application %2$s.
                            %1$s contains %3$s record(s)"""
            ),
            UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données "%1$s" a bien été supprimé pour l'application %2$s.
                            %1$s contient %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file "%1$s" has been successfully deleted for the application %2$s.
                            %1$s contains %3$s record(s)"""
            )
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
     * Le texte d'accompagnement encadre désormais le message technique brut
     * pour guider l'utilisateur vers une reprise ou un contact administrateur.
     * Placeholders :
     *   %1$s = titre ( localisé )
     *   %2$s = titre de l'application ( localisé )
     *   %3$s = message d'erreur technique brut ( JSON , tel que renvoyé par le backend )
     */
    private static final Map<Boolean, Map<Locale, String>> ERROR_BODIES = Map.of(
            Boolean.TRUE, Map.of(
                    Locale.FRENCH, """
                            Une erreur est survenue lors de l'opération sur le référentiel %1$s de l'application %2$s.
                            Vous trouverez ci-dessous le message d'erreur technique associé :

                            %3$s

                            Vous pouvez vérifier vos données et réessayer.
                            Si le problème persiste, nous vous invitons à contacter le(s) administrateur(s) du système d'information.""",
                    Locale.ENGLISH, """
                            An error occurred while processing the reference dataset "%1$s" in the application %2$s.
                            The technical error message is provided below:

                            %3$s

                            You may review your data and try again.
                            If the issue persists, please contact the information system administrator(s)."""
            ),
            Boolean.FALSE, Map.of(
                    Locale.FRENCH, """
                            Une erreur est survenue lors de l'opération sur le type de données %1$s de l'application %2$s.
                            Vous trouverez ci-dessous le message d'erreur technique associé :

                            %3$s

                            Vous pouvez vérifier vos données et réessayer.
                            Si le problème persiste, nous vous invitons à contacter le(s) administrateur(s) du système d'information.""",
                    Locale.ENGLISH, """
                            An error occurred while processing the data type dataset "%1$s" in the application %2$s.
                            The technical error message is provided below:

                            %3$s

                            You may review your data and try again.
                            If the issue persists, please contact the information system administrator(s)."""
            )
    );

    private static final String MAIL_VERIFICATION_TEMPLATE = """
            %2$s%n%nVotre clé de connexion est : %n%1$s
            %3$sYour connection key is: %n%1$s
            """;
    private static final String MAIL_MESSAGE_TEMPLATE =
            "Bonjour %1$s%n%n" +
            "%2$s%n" +
            "L'équipe d'OpenAdom";
    private final JavaMailSender mailSender;
    private final ServiceContainer serviceContainer;
    @Value("${spring.mail.from}")
    String mailFrom;
    private LocaleResolver localeResolver;

    @Autowired
    public EmailService(JavaMailSender mailSender, LocaleResolver localeResolver, ServiceContainer serviceContainer) {
        this.mailSender = mailSender;
        this.localeResolver = localeResolver;
        this.serviceContainer = serviceContainer;
    }

    @Override
    public void sendEmail(final String login, final String to, final String subject, final String message) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(to);
        mailMessage.setFrom(mailFrom);
        mailMessage.setSubject(subject);
        mailMessage.setText(String.format(MAIL_MESSAGE_TEMPLATE, login, message));
        mailSender.send(mailMessage);
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
        mailMessage.setTo(to);
        mailMessage.setFrom(OPENADOM_INRAE_FR);
        mailMessage.setSubject(subject);
        mailMessage.setText(
                String.format(
                        fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID),
                        internationnalizedDataName
                )
        );
        mailSender.send(mailMessage);
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
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom(OPENADOM_INRAE_FR);
        mailMessage.setSubject(subject);
        mailMessage.setText(text);

        mailSender.send(mailMessage);
    }

    @Override
    public void sendUpoadErrorsMail(Locale locale, String application, String dataName, boolean isReference, OreSiUser currentUser, String body) {
        // #477 - Le sujet et le corps sont désormais différenciés selon
        // qu'il s'agit d'un référentiel ou d'un type de données , et le
        // corps encadre le message technique brut par un texte
        // d'accompagnement ( document ODT joint à l'issue ).
        Locale effectiveLocale = Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH;
        String subject = ERROR_SUBJECTS.get(isReference).get(effectiveLocale)
                .formatted(dataName, application);
        String text = ERROR_BODIES.get(isReference).get(effectiveLocale)
                .formatted(dataName, application, body);

        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom(OPENADOM_INRAE_FR);
        mailMessage.setSubject(subject);
        mailMessage.setText(text);

        mailSender.send(mailMessage);
    }

    public enum UPLOAD_STATE {
        UPLOADED,
        PUBLISHED,
        UNPUBLISHED,
        DELETED
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