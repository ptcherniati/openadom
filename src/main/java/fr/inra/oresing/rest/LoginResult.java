package fr.inra.oresing.rest;

import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class LoginResult {
    UUID id;
    String login;
    boolean authorizedForApplicationCreation;
    boolean superadmin;
    List<String> authorizations;
}