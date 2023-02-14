package fr.inra.oresing.rest;

import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class CreateRightsRequestRequest {
    UUID id;
    Map<String, String> fields;
    List<CreateAuthorizationRequest> rightsRequest;
}