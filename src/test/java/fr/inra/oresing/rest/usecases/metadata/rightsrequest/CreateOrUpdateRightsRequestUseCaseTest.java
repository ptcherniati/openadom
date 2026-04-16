package fr.inra.oresing.rest.usecases.metadata.rightsrequest;

import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.services.RightsRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class CreateOrUpdateRightsRequestUseCaseTest {

    @Mock
    private RightsRequestService rightsRequestService;

    private CreateOrUpdateRightsRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrUpdateRightsRequestUseCase(rightsRequestService);
    }

    @Test
    void execute_returnsRightsRequestUUID() {
        UUID expectedId = UUID.randomUUID();
        CreateRightsRequestRequest request = mock(CreateRightsRequestRequest.class);

        when(rightsRequestService.createOrUpdate(request, "app"))
                .thenReturn(expectedId);

        UUID result = useCase.execute(request, "app");

        assertEquals(expectedId, result);
    }
}
