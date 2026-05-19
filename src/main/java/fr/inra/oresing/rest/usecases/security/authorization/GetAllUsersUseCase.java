package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.services.DefaultAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetAllUsersUseCase {
    private final DefaultAuthorizationService authorizationService;

    public GetAllUsersUseCase(DefaultAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public List<OreSiUser> execute() {
        return authorizationService.getAllUsers();
    }
}