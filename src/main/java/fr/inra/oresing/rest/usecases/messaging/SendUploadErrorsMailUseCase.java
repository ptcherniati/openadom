package fr.inra.oresing.rest.usecases.messaging;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.mail.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SendUploadErrorsMailUseCase {

    private final EmailService emailService;

    public void execute(Locale locale, String application, String dataName, String fileName, boolean isReference, OreSiUser currentUser, String body) {
        emailService.sendUpoadErrorsMail(locale, application, dataName, fileName, isReference, currentUser, body);
    }
}