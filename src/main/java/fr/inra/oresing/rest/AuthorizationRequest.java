package fr.inra.oresing.rest;

import lombok.Value;

import java.util.UUID;

@Value
public class AuthorizationRequest {

    String applicationNameOrId;

    String dataType;

    UUID authorizationId;
}