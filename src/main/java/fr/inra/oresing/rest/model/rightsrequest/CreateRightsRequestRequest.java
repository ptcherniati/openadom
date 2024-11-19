package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.rest.model.authorization.CreateAuthorizationRequest;

import java.util.Map;
import java.util.UUID;


public record CreateRightsRequestRequest(
        UUID id,
        Map<String, String> fields,
        CreateAuthorizationRequest rightsRequest,
        boolean setted,
        String comment
) {
}