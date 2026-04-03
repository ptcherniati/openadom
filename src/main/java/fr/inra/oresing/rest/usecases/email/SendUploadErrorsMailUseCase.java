package fr.inra.oresing.rest.usecases.email;

import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.domain.OreSiUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SendUploadErrorsMailUseCase {

    private final EmailService emailService;

    public void execute(Locale locale, String application, String dataName, OreSiUser currentUser, String body) {
        emailService.sendUpoadErrorsMail(locale, application, dataName, currentUser, body);
    }
}
