package fr.inra.oresing.mail;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
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
public class EmailService implements Email, ServiceContainerBean {
    @Value("${spring.mail.from}")
    String mailFrom;
    @Autowired
    private final JavaMailSender mailSender;
    private static final String NEW_ACCOUNT_SUBJECT = "Création de compte / Account creation";
    private static final String NEW_ACCOUNT_FR = "Vous venez de créer un compte sur l'application OPENAdom. %n" +
                                                 "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.%n\n";
    private static final String NEW_ACCOUNT_EN = "You have just created an account on the OPENAdomoresie application. %n" +
                                                 "To validate your e-mail, enter the validation key when connecting.%n\n";
    private static final String EMAIL_CHANGED_SUBJECT = "Validation email / Email validation";
    private static final String EMAIL_CHANGED_FR = "Vous venez de modifier votre email. %n" +
                                                   "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.%n\n";
    private static final String EMAIL_CHANGED_EN = "You have just changed your email. %n" +
                                                   "To validate your e-maioresil, enter the validation key when connecting.%n\n";
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
                    Locale.ENGLISH, "Your file has been register"
            ),
            UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, "Votre fichier a bien été supprimé",
                    Locale.ENGLISH, "Your file has been deleted"
            )
    );
    private static final Map<UPLOAD_STATE, Map<Locale, String>> SUCCESS_UPLOAD_TEXTS = Map.of(
            UNPUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données %1$s a bien été dépublié pour l'application %2$s.
                            %1$s continent %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file %1$s has been successfully unpublished for the application %2$s.
                            %1$s continent %3$s record(s)"""
            ),
            UPLOAD_STATE.PUBLISHED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données %1$s a bien été publié pour l'application %2$s.
                            %1$s continent %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file %1$s has been successfully published for the application %2$s.
                            %1$s continent %3$s record(s)"""
            ),
            UPLOAD_STATE.UPLOADED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données %1$s a bien été enregistré pour l'application %2$s.
                            %1$s continent %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file %1$s has been successfully register for the application %2$s.
                            %1$s continent %3$s record(s)"""
            ),
            UPLOAD_STATE.DELETED, Map.of(
                    Locale.FRENCH, """
                            Le fichier de données %1$s a bien été supprimé pour l'application %2$s.
                            %1$s continent %3$s enregistrement(s)""",
                    Locale.ENGLISH, """
                            The data file %1$s has been successfully deleted for the application %2$s.
                            %1$s continent %3$s record(s)"""
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
    public static final String MSG_ERROR_SUBJECT_FR = "Une erreur c'est produite lors de  l'opération sur le type de   données % de l'application %s";
    public static final String MSG_ERROR_SUBJECT_EN = "An error occurred while operating on data type % of application %s";

    @Autowired
    private LocaleResolver localeResolver;

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
    public void setServiceContainer(ServiceContainer serviceContainer) {

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

        MESSAGES(final String subject, final String title_fr, final String title_en) {
            this.subject = subject;
            this.title_fr = title_fr;
            this.title_en = title_en;
        }

        final String subject;
        final String title_fr;
        final String title_en;
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
        mailMessage.setFrom("openadom@inrae.fr");
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
    public void sendUpoadSuccessMail(Application application, String dataName, UPLOAD_STATE uploadState, Locale locale, DataVersioningResult dataVersioningResult, OreSiUser currentUser) {
        long count = dataVersioningResult.dataSynthesis().stream()
                .filter(dataSynthesis -> dataSynthesis.getReferenceType().equals(dataName))
                .findFirst()
                .map(ApplicationResult.DataSynthesis::getLineCount)
                .orElse(0);

        String localizedApplicationName = application.getLocalizedLocalName(locale);
        String localizedDataName = application.getLocalizedDataName(locale, dataName);
        localizedDataName = localizedDataName == null ? dataName : localizedDataName;
        String subject = SUCCESS_UPLOAD_SUBJECTS.get(uploadState)
                .get(locale.equals(Locale.ENGLISH) ? Locale.ENGLISH : Locale.FRENCH);
        String text = SUCCESS_UPLOAD_TEXTS.get(uploadState)
                .get(locale.equals(Locale.ENGLISH) ? Locale.ENGLISH : Locale.FRENCH)
                .formatted(
                        localizedDataName,
                        localizedApplicationName,
                        count
                );
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom("openadom@inrae.fr");
        mailMessage.setSubject(subject);
        mailMessage.setText(text);

        mailSender.send(mailMessage);
    }

    @Override
    public void sendUpoadErrorsMail(Locale application, String dataName, String locale, OreSiUser currentUser, String body) {
        final SimpleMailMessage  mailMessage = new SimpleMailMessage();
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom("openadom@inrae.fr");
        mailMessage.setSubject(Locale.ENGLISH.getLanguage().equals(locale)?MSG_ERROR_SUBJECT_EN:MSG_ERROR_SUBJECT_FR);
        mailMessage.setText(body);

        mailSender.send(mailMessage);
    }

}