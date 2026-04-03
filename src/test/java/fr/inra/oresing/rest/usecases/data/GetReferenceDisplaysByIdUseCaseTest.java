package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetReferenceDisplaysByIdUseCaseTest {

    @Mock
    private DataService dataService;

    private GetReferenceDisplaysByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetReferenceDisplaysByIdUseCase(dataService);
    }

    @Test
    void execute_returnsReferenceDisplays() {
        Application application = mock(Application.class);
        Set<String> ids = Set.of("id1");
        Map<Ltree, List<DataValue>> expected = mock(Map.class);

        when(dataService.getReferenceDisplaysById(application, ids))
                .thenReturn(expected);

        Map<Ltree, List<DataValue>> result = useCase.execute(application, ids);

        assertEquals(expected, result);
    }
}
