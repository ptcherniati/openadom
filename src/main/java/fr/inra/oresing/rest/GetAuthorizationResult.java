package fr.inra.oresing.rest;

import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class GetAuthorizationResult {
    UUID uuid;
    String name;
    Set<OreSiUser> users;
    UUID application;
    Map<String, Map<OperationType, List<AuthorizationParsed>>> authorizations;
    Map<String, Map<OperationType, List<Authorization>>> publicAuthorizations;
    AuthorizationsResult authorizationsForUser;
}