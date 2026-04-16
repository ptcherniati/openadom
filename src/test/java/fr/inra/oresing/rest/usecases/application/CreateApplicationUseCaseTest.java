package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.services.ApplicationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class CreateApplicationUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private CreateApplicationUseCase useCase;

    @Test
    void execute_shouldDelegateToApplicationService() throws IOException {
        // Given
        Consumer<ReactiveResult> consumer = mock(Consumer.class);
        String name = "testApp";
        DataFile dataFile = mock(DataFile.class);
        String comment = "Test comment";

        // When
        useCase.execute(consumer, name, dataFile, comment);

        // Then
        verify(applicationService).createApplication(consumer, name, dataFile, comment);
    }
}
