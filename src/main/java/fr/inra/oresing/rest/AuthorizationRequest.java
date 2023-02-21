package fr.inra.oresing.rest;

import lombok.Value;

import java.util.UUID;

@Value
public class AuthorizationRequest {

    String applicationNameOrId;

    UUID authorizationId;
}