package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.domain.filesenderclient.MessageInformations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class SendZipLinkByMailUseCaseTest {

    @Mock
    private DataService dataService;

    @InjectMocks
    private SendZipLinkByMailUseCase useCase;

    @Test
    void execute_shouldDelegateToDataService() {
        // Given
        Path filePath = Paths.get("/tmp/test.zip");
        MessageInformations messageInformations = mock(MessageInformations.class);
        OreSiUser currentUser = new OreSiUser();

        // When
        useCase.execute(filePath, messageInformations, currentUser);

        // Then
        verify(dataService).sendZipLinkByMail(filePath, messageInformations, currentUser);
    }
}
