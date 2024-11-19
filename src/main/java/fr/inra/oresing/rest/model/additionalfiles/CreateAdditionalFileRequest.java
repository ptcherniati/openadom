package fr.inra.oresing.rest.model.additionalfiles;

import fr.inra.oresing.rest.model.authorization.AdditionalFileAuthorizationRequest;
import fr.inra.oresing.rest.model.authorization.CreateAuthorizationRequest;

import java.util.Map;
import java.util.UUID;


public record CreateAdditionalFileRequest(
        UUID id,
        String comment,
        String fileType,
        Map<String, String> fields,
        AdditionalFileAuthorizationRequest associates,
        Boolean forApplication
) {
}