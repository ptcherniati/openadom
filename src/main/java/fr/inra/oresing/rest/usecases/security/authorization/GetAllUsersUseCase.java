package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.services.AuthorizationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetAllUsersUseCase {
    private final AuthorizationService authorizationService;

    public GetAllUsersUseCase(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public List<OreSiUser> execute() {
        return authorizationService.getAllUsers();
    }
}
