package fr.inra.oresing.domain.services.authorization;

import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

public interface AuthorizationService {
    AuthorizationsResult getAuthorizationsForUserAndPublic(String applicationName, String currentUser);
}
