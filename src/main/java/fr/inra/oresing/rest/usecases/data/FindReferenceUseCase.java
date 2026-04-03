package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.ApplicationService;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.List;

@Component
public class FindReferenceUseCase {
    private final ApplicationService applicationService;
    private final DataService dataService;

    public FindReferenceUseCase(ApplicationService applicationService, DataService dataService) {
        this.applicationService = applicationService;
        this.dataService = dataService;
    }

    public List<DataValue> execute(String nameOrId, String refType, MultiValueMap<String, String> params) {
        Application application = applicationService.getApplication(nameOrId);
        return dataService.findReference(nameOrId, refType, params);
    }
}
