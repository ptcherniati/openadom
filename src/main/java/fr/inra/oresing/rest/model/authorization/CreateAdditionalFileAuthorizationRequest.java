package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.additionalfiles.OperationAdditionalFileType;
import lombok.Value;

import java.util.*;


@Value
public class CreateAdditionalFileAuthorizationRequest {
    UUID uuid;

    String name;

    Set<UUID> usersId;

    String applicationNameOrId;

    Map<OperationAdditionalFileType, List<String>> additionalFiles = new HashMap<>();
}