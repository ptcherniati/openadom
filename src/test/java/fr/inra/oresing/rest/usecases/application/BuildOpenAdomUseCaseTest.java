package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.model.application.ApplicationResult;
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
class BuildOpenAdomUseCaseTest {

    @Mock
    private ApplicationService applicationService;

    private BuildOpenAdomUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BuildOpenAdomUseCase(applicationService);
    }

    @Test
    void execute_returnsApplicationResult() {
        Application application = mock(Application.class);
        ApplicationResult expected = mock(ApplicationResult.class);

        when(applicationService.buildOpenAdom(application, new String[]{"ALL"}))
                .thenReturn(expected);

        ApplicationResult result = useCase.execute(application, new String[]{"ALL"});

        assertEquals(expected, result);
    }
}
