package fr.inra.oresing.mail;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import org.springframework.scheduling.annotation.Async;

import java.util.Locale;

public interface Email {
    @Async
    void sendEmail(String login, String to, String subject, String message);

    void sendEmailValidation(String login, String email, String verificationKey, EmailService.MESSAGES messages);

    @Async
    void sendUploadZipEmail(
            String to,
            String subject,
            String message,
            String downloadUrl,
            FileSenderInternationalisation fileSenderInternationalisation,
            String internationnalizedDataName);

    @Async
    void sendUpoadSuccesmail(Application application, String dataName, EmailService.UPLOAD_STATE uploadState, Locale locale, DataVersioningResult dataVersioningResult, OreSiUser currentUser);
}
