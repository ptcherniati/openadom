package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.model.authorization.UserAuthorizationForApplication;
import fr.inra.oresing.rest.services.ApplicationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetApplicationAuthorizationsUseCase {
    private final ApplicationService applicationService;
    private final AuthenticationService authenticationService;

    public GetApplicationAuthorizationsUseCase(
            ApplicationService applicationService,
            AuthenticationService authenticationService) {
        this.applicationService = applicationService;
        this.authenticationService = authenticationService;
    }

    public List<UserAuthorizationForApplication> execute(String nameOrId) {
        Application application = applicationService.getApplication(nameOrId);
        return authenticationService.getApplicationAuthorizations(application);
    }
}
