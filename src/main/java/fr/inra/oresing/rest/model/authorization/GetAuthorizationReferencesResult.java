package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.OperationReferenceType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public record GetAuthorizationReferencesResult(UUID uuid, String name, Set<OreSiUser> users, UUID application,
                                               Map<OperationReferenceType, List<String>> authorizations) {
}