package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;

import java.util.List;
import java.util.Map;

public record AuthorizationsForApplicationUser(
        Application application,
        boolean isApplicationManager,
        boolean isUserManager,
        Map<String, List<AuthorizationParsed>> userAuthorizations,
        Map<String, AuthorizationParsed> publicAuthorizations
) {
}
