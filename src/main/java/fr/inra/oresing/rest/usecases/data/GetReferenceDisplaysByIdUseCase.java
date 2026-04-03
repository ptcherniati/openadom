package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GetReferenceDisplaysByIdUseCase {
    private final DataService dataService;

    public GetReferenceDisplaysByIdUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public Map<Ltree, List<DataValue>> execute(Application application, Set<String> listOfDataIds) {
        return dataService.getReferenceDisplaysById(application, listOfDataIds);
    }
}
