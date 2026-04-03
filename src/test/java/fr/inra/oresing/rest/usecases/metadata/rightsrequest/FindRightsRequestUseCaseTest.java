package fr.inra.oresing.rest.usecases.metadata.rightsrequest;

import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.services.RightsRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class FindRightsRequestUseCaseTest {

    @Mock
    private RightsRequestService rightsRequestService;

    private FindRightsRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new FindRightsRequestUseCase(rightsRequestService);
    }

    @Test
    void execute_returnsFindRightsRequestResult() {
        GetRightsRequestResult expected = mock(GetRightsRequestResult.class);
        RightsRequestInfos infos = mock(RightsRequestInfos.class);

        when(rightsRequestService.findRightsRequest("app", infos))
                .thenReturn(expected);

        GetRightsRequestResult result = useCase.execute("app", infos);

        assertEquals(expected, result);
    }
}
