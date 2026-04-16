package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.model.data.LineCheckerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetCheckedFormatComponentsUseCaseTest {

    @Mock
    private DataService dataService;

    private GetCheckedFormatComponentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCheckedFormatComponentsUseCase(dataService);
    }

    @Test
    void execute_returnsCheckedFormatComponents() {
        Map<String, Map<String, LineCheckerResult>> expected = mock(Map.class);

        when(dataService.getCheckedFormatComponents("app", "data"))
                .thenReturn(expected);

        Map<String, Map<String, LineCheckerResult>> result = useCase.execute("app", "data");

        assertEquals(expected, result);
    }
}
