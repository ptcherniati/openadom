package fr.inra.oresing.rest.usecases.metadata.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetSynthesisUseCase {
    private final SynthesisService synthesisService;

    public GetSynthesisUseCase(SynthesisService synthesisService) {
        this.synthesisService = synthesisService;
    }

    public Map<String, List<OreSiSynthesis>> execute(String nameOrId, String dataType) {
        return synthesisService.getSynthesis(nameOrId, dataType);
    }
}
