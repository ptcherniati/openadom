package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.OperationReferenceType;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class GetAuthorizationReferencesResult {
    UUID uuid;
    String name;
    Set<OreSiUser> users;
    UUID application;
    Map<OperationReferenceType, List<String>> authorizations;
    Map<OperationReferenceType, List<String>> publicAuthorizations;
    AuthorizationsReferencesResult authorizationsForUser;
}