package fr.inra.oresing.rest;

import fr.inra.oresing.persistence.OperationReferenceType;
import lombok.Value;

import java.util.*;

@Value
public class CreateReferenceAuthorizationRequest {
    UUID uuid;

    String name;

    Set<UUID> usersId;

    String applicationNameOrId;

    Map<OperationReferenceType, List<String>> references = new HashMap<>();
}