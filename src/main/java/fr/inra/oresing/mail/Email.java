package fr.inra.oresing.mail;

import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import org.springframework.scheduling.annotation.Async;

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
}
