package fr.inra.oresing.rest.usecases.metadata;

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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@Tag("use-cases")
class GetSynthesisUseCaseTest {

    @Mock
    private SynthesisService synthesisService;

    private GetSynthesisUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSynthesisUseCase(synthesisService);
    }

    @Test
    void execute_returnsSynthesisMap() {
        Map<String, List<OreSiSynthesis>> expected = mock(Map.class);

        when(synthesisService.getSynthesis("app", "dataType"))
                .thenReturn(expected);

        Map<String, List<OreSiSynthesis>> result = useCase.execute("app", "dataType");

        assertEquals(expected, result);
    }
}
