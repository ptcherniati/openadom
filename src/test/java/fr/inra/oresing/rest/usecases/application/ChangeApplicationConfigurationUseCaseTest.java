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

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class ChangeApplicationConfigurationUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ChangeApplicationConfigurationUseCase useCase;

    @Test
    void execute_shouldDelegateToApplicationService() {
        // Given
        Consumer<ReactiveResult> consumer = mock(Consumer.class);
        String nameOrId = "testApp";
        DataFile dataFile = mock(DataFile.class);
        String comment = "Update config";
        UUID expected = UUID.randomUUID();
        
        when(applicationService.changeApplicationConfiguration(consumer, nameOrId, dataFile, comment))
            .thenReturn(expected);

        // When
        UUID result = useCase.execute(consumer, nameOrId, dataFile, comment);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(applicationService).changeApplicationConfiguration(consumer, nameOrId, dataFile, comment);
    }
}
