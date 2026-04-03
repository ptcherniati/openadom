package fr.inra.oresing.rest.usecases.metadata.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetSynthesisWithVariableUseCaseTest {

    @Mock
    private SynthesisService synthesisService;

    private GetSynthesisWithVariableUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSynthesisWithVariableUseCase(synthesisService);
    }

    @Test
    void execute_returnsSynthesisMap() {
        Map<String, List<OreSiSynthesis>> expected = mock(Map.class);

        when(synthesisService.getSynthesis("app", "dataType", "variable"))
                .thenReturn(expected);

        Map<String, List<OreSiSynthesis>> result = useCase.execute("app", "dataType", "variable");

        assertEquals(expected, result);
    }
}
