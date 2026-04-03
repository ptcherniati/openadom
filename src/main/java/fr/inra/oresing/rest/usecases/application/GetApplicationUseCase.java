package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import org.springframework.stereotype.Component;

@Component
public class GetApplicationUseCase {
    private final ApplicationService applicationService;

    public GetApplicationUseCase(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Application execute(String nameOrId) {
        return applicationService.getApplication(nameOrId);
    }
}
