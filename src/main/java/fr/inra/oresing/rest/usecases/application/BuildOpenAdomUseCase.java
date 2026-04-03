package fr.inra.oresing.rest.usecases.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ApplicationService;
import org.springframework.stereotype.Component;

@Component
public class BuildOpenAdomUseCase {
    private final ApplicationService applicationService;

    public BuildOpenAdomUseCase(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public ApplicationResult execute(Application application, String[] filter) {
        return applicationService.buildOpenAdom(application, filter);
    }
}
