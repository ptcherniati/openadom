package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;
import java.util.UUID;

@Value
public class CreateAdditionalFileRequest {
    UUID id;
    String fileType;
    Map<String, String> fields;
    CreateAuthorizationRequest associates;
}