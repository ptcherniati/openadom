package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.rest.data.DataService;
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
class GetFormatCheckedUseCaseTest {

    @Mock
    private DataService dataService;

    private GetFormatCheckedUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFormatCheckedUseCase(dataService);
    }

    @Test
    void execute_returnsFormatChecked() {
        Map<String, Map<String, LineChecker>> expected = mock(Map.class);

        when(dataService.getFormatChecked("app", "ref"))
                .thenReturn(expected);

        Map<String, Map<String, LineChecker>> result = useCase.execute("app", "ref");

        assertEquals(expected, result);
    }
}
