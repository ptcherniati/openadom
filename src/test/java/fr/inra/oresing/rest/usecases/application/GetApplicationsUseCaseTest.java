package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("use-cases")
@ExtendWith(MockitoExtension.class)
class GetApplicationsUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private GetApplicationsUseCase useCase;

    @Test
    void execute_shouldDelegateToApplicationService() {
        // Given
        List<ApplicationInformation> filters = List.of(ApplicationInformation.ALL);
        Flux<ReactiveResult> expected = Flux.empty();
        
        when(applicationService.getApplications(filters)).thenReturn(expected);

        // When
        Flux<ReactiveResult> result = useCase.execute(filters);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(applicationService).getApplications(filters);
    }
}
