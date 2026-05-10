package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.authorization.AuthorizationsResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public record GetAuthorizationResult(
        UUID uuid,
        String name,
        String description,
        Set<OreSiUser> users,
        Map<String, AuthorizationParsed> authorizations,
        Map<String, List<AuthorizationParsed>> authorizationforPublic,
        AuthorizationsResult authorizationsForUser
) {
}