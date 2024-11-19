package fr.inra.oresing.domain.services.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;

import java.util.List;
import java.util.Map;

public interface SynthesisService {
    Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType, String component) ;
}
