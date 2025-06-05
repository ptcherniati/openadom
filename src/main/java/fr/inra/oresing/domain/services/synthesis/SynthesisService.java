package fr.inra.oresing.domain.services.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.rest.services.ServiceContainer;

import java.util.List;
import java.util.Map;

public interface SynthesisService {
    int deleteSynthesis(String nameOrId, String dataType, String variable);

    int deleteSynthesis(String nameOrId, String dataType);

    Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType, String component);

    Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataType);

    Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataName, String componentName);
}