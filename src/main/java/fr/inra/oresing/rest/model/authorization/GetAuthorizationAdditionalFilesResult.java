package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.OperationAdditionalFileType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public record GetAuthorizationAdditionalFilesResult(UUID uuid, String name, Set<OreSiUser> users, UUID application,
                                                    Map<OperationAdditionalFileType, List<String>> authorizations) {
}