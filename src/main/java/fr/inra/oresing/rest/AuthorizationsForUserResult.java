package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;

@Value
public class AuthorizationsForUserResult {
    Map<String, Map<Roles,Boolean>> authorizations;
    String applicationName;
    Boolean isAdministrator;

    String userId;

    public static enum Roles{
        UPLOAD,
        DOWNLOAD,
        READ,
        ADMIN, DELETE
    }

}