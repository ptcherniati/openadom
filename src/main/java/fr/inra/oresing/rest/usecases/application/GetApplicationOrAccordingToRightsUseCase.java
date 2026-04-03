package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ApplicationService;
import org.springframework.stereotype.Component;

@Component
public class GetApplicationOrAccordingToRightsUseCase {
    private final ApplicationService applicationService;

    public GetApplicationOrAccordingToRightsUseCase(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Application execute(String nameOrId) {
        return applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
    }
}
