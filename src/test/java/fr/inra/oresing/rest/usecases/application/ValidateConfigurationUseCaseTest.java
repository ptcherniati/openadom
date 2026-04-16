package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.services.ApplicationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class ValidateConfigurationUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ValidateConfigurationUseCase useCase;

    @Test
    void execute_shouldDelegateToApplicationService() {
        // Given
        Consumer<ReactiveResult> consumer = mock(Consumer.class);
        DataFile dataFile = mock(DataFile.class);
        Application expected = new Application();
        
        when(applicationService.validateConfiguration(consumer, dataFile)).thenReturn(expected);

        // When
        Application result = useCase.execute(consumer, dataFile);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(applicationService).validateConfiguration(consumer, dataFile);
    }
}
