package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.persistence.AuthenticationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetAdminAuthorizationsUseCase {
    private final AuthenticationService authenticationService;

    public GetAdminAuthorizationsUseCase(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public List<LoginAdminResult> execute() {
        return authenticationService.getAdminAuthorizations();
    }
}