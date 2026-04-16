package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetDataColumnUseCaseTest {

    @Mock
    private DataService dataService;

    private GetDataColumnUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetDataColumnUseCase(dataService);
    }

    @Test
    void execute_returnsDataColumn() {
        Application application = mock(Application.class);
        List<List<String>> expected = mock(List.class);

        when(dataService.getDataColumn(application, "ref", "col"))
                .thenReturn(expected);

        List<List<String>> result = useCase.execute(application, "ref", "col");

        assertEquals(expected, result);
    }
}
