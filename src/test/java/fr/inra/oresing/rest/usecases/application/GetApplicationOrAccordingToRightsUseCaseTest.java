package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetApplicationOrAccordingToRightsUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    private GetApplicationOrAccordingToRightsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetApplicationOrAccordingToRightsUseCase(applicationService);
    }

    @Test
    void execute_returnsApplication() {
        Application expected = mock(Application.class);

        when(applicationService.getApplicationOrApplicationAccordingToRights("app"))
                .thenReturn(expected);

        Application result = useCase.execute("app");

        assertEquals(expected, result);
    }
}
