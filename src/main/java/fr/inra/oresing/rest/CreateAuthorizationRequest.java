package fr.inra.oresing.rest;

import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class CreateAuthorizationRequest {
    UUID uuid;

    String name;

    Set<UUID> usersId;

    String applicationNameOrId;

    Map<String, Map<OperationType, List<Authorization>>> authorizations;
}