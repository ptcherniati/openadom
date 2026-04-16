package fr.inra.oresing.rest.usecases.security.authentication;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final AuthenticationService authenticationService;

    public OreSiUser execute() {
        return authenticationService.getCurrentUser();
    }
}
