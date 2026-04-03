package fr.inra.oresing.rest.usecases.messaging;

import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.domain.OreSiUser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static org.mockito.Mockito.verify;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class SendUploadErrorsMailUseCaseTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private SendUploadErrorsMailUseCase useCase;

    @Test
    void execute_shouldDelegateToEmailService() {
        // Given
        Locale locale = Locale.FRENCH;
        String application = "testApp";
        String dataName = "testData";
        OreSiUser currentUser = new OreSiUser();
        String body = "Error details";

        // When
        useCase.execute(locale, application, dataName, currentUser, body);

        // Then
        verify(emailService).sendUpoadErrorsMail(locale, application, dataName, currentUser, body);
    }
}