package fr.inra.oresing.domain.services.synthesis;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;

import java.util.List;
import java.util.Map;

public interface SynthesisService extends ServiceContainerBean {
    int deleteSynthesis(String nameOrId, String dataType, String variable);

    int deleteSynthesis(String nameOrId, String dataType);

    Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType, String component) ;

    Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataType);

    Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataName, String componentName);

    void setServiceContainer(ServiceContainer serviceContainer);
}
