package fr.inra.oresing.mail;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;

import java.util.Locale;

public interface Email {
    void sendEmail(String login, String to, String subject, String message);
    //void sendEmailWithAttachment(String login, String email, String title, String messages, File attached) throws IOException, MessagingException;

    void sendEmailValidation(String login, String email, String verificationKey, EmailService.MESSAGES messages);

    void sendUploadZipEmail(
            String to,
            String subject,
            String message,
            String downloadUrl,
            FileSenderInternationalisation fileSenderInternationalisation,
            String internationnalizedDataName);

    void sendUpoadSuccessMail(Application application, String dataName, EmailService.UPLOAD_STATE uploadState, Locale locale, DataVersioningResult dataVersioningResult, OreSiUser currentUser);

    void sendUpoadErrorsMail(Locale locale, String application, String dataname, OreSiUser body, String currentUser);
}