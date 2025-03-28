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
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
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
    @Autowired
    private MessageSource messageSource;

    @Autowired
    private LocaleResolver localeResolver;

    @Async
    @Override
    public void sendEmail(final String login, final String to, final String subject, final String message) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(to);
        mailMessage.setFrom(mailFrom);
        mailMessage.setSubject(subject);
        mailMessage.setText(String.format(messageSource.getMessage("mail.messageTemplate", null, Locale.getDefault()), login, message));
        mailSender.send(mailMessage);
    }

    @Override
    public void sendEmailValidation(final String login, final String email, final String verificationKey, final MESSAGES messages) {
        String message = String.format(messageSource.getMessage("mail.verificationTemplate", null, Locale.getDefault()), verificationKey, messageSource.getMessage(messages.title_fr, null, Locale.getDefault()), messageSource.getMessage(messages.title_en, null, Locale.getDefault()));
        sendEmail(login, email, messages.subject, message);
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {

    }

    public enum UPLOAD_STATE {
        UPLOADED,
        PUBLISHED,
        UNPUBLISHED,
        DELETED;
    }

    public enum MESSAGES {

        NEW_ACCOUNT("mail.newAccount.subject", "mail.newAccount.body.fr", "mail.newAccount.body.en"),
        NEW_EMAIL("mail.emailChanged.subject", "mail.emailChanged.body.fr", "mail.emailChanged.body.en"),
        VALIDATION_KEY("mail.validationKey.subject", "", "");

        MESSAGES(final String subject, final String title_fr, final String title_en) {
            this.subject = subject;
            this.title_fr = title_fr;
            this.title_en = title_en;
        }

        final String subject;
        final String title_fr;
        final String title_en;
    }

    private static final Map<UPLOAD_STATE, String> SUCCESS_UPLOAD_SUBJECTS = Map.of(
            UPLOAD_STATE.UNPUBLISHED, "mail.upload.unpublished.subject",
            UPLOAD_STATE.PUBLISHED, "mail.upload.published.subject",
            UPLOAD_STATE.UPLOADED, "mail.upload.uploaded.subject",
            UPLOAD_STATE.DELETED, "mail.upload.deleted.subject"
    );

    private static final Map<UPLOAD_STATE, String> SUCCESS_UPLOAD_TEXTS = Map.of(
            UPLOAD_STATE.UNPUBLISHED, "mail.upload.unpublished.body",
            UPLOAD_STATE.PUBLISHED, "mail.upload.published.body",
            UPLOAD_STATE.UPLOADED, "mail.upload.uploaded.body",
            UPLOAD_STATE.DELETED, "mail.upload.deleted.body"
    );

    @Async
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

        // Récupération des clés des messages à partir des maps
        String subjectKey = SUCCESS_UPLOAD_SUBJECTS.get(uploadState);
        String textKey = SUCCESS_UPLOAD_TEXTS.get(uploadState);

        // Récupération des messages à partir des fichiers de propriétés
        String subject = messageSource.getMessage(subjectKey, null, locale);
        String text = String.format(messageSource.getMessage(textKey, null, locale), localizedDataName, localizedApplicationName, count);

        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom("openadom@inrae.fr");
        mailMessage.setSubject(subject);
        mailMessage.setText(text);

        mailSender.send(mailMessage);
    }

    @Override
    public void sendUpoadErrorsMail(Locale application, String dataName, String locale, OreSiUser currentUser, String body) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(currentUser.getEmail());
        mailMessage.setFrom("openadom@inrae.fr");
        String subject = messageSource.getMessage("mail.error.subject", new Object[]{dataName, application}, new Locale(locale));
        mailMessage.setSubject(subject);
        mailMessage.setText(body);
        mailSender.send(mailMessage);
    }

}