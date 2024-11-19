package fr.inra.oresing.rest.application;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Transactional(readOnly = true)

public class ApplicationService {
    @Autowired
    private OreSiRepository repository;
    @Autowired
    private AuthenticationService authenticationService;

    public Application getApplication(final String nameOrId) {
        // TODO filtre tag hidden boucle sur les reference et les datatypes
        authenticationService.setRoleForClient();
        // Application result = repo.application().findApplication(nameOrId);
        return getApplicationRepository().findApplication(nameOrId);
    }

    public Application getApplicationOrApplicationAccordingToRights(final String nameOrId) {
        authenticationService.setRoleForClient();
        try {
            return getApplicationRepository().findApplication(nameOrId);
        } catch (final NoSuchApplicationException e) {
            authenticationService.setRoleAdmin();
            return getApplicationRepository().findApplication(nameOrId).applicationAccordingToRights();
        }
    }

    private ApplicationRepository getApplicationRepository() {
        return repository.application();
    }
}
