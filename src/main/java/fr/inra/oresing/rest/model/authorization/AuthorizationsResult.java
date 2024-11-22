package fr.inra.oresing.rest.model.authorization;

import java.util.List;
import java.util.Map;


public record AuthorizationsResult(
        Map<String, List<AuthorizationParsed>> userAuthorization,
        Map<String, AuthorizationParsed> publicAuthorization,
        String applicationName,
        Boolean applicationCreator,
        Boolean applicationManager,
        Boolean isAdministrator,
        Boolean userManager,
        Boolean applicationUser, Boolean activeApplicationUser
) {
    public AuthorizationsResult(Map<String, List<AuthorizationParsed>> userAuthorization, Map<String, AuthorizationParsed> publicAuthorization, String applicationName, Boolean applicationCreator, Boolean applicationManager, Boolean userManager, Boolean applicationUser, Boolean activeApplicationUser) {
        this(userAuthorization, publicAuthorization, applicationName, applicationCreator, applicationManager, applicationCreator||applicationManager, userManager, applicationUser, activeApplicationUser);
    }

    public AuthorizationsResult {
    }
}