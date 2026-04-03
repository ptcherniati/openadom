package fr.inra.oresing.rest.usecases.metadata.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BuildSynthesisUseCase {
    private final SynthesisService synthesisService;

    public BuildSynthesisUseCase(SynthesisService synthesisService) {
        this.synthesisService = synthesisService;
    }

    public Map<String, List<OreSiSynthesis>> execute(String nameOrId, String dataType, String variable) {
        return synthesisService.buildSynthesis(nameOrId, dataType, variable);
    }
}
