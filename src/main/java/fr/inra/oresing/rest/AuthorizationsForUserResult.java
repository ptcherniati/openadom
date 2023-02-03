package fr.inra.oresing.rest;

import lombok.Value;

import java.util.Map;

import static fr.inra.oresing.rest.AuthorizationsForUserResult.Roles.*;

@Value
public class AuthorizationsForUserResult {
    public static   Map<AuthorizationsForUserResult.Roles, Boolean> DEFAULT_REFERENCE_ROLES = Map.of(
            DOWNLOAD, true,
            READ, true,
            DELETE, false,
            UPLOAD, false,
            ADMIN, false,
            PUBLICATION, false,
            ANY, true
    );
    public static   Map<AuthorizationsForUserResult.Roles, Boolean> DEFAULT_ROLES = Map.of(
            DOWNLOAD, false,
            READ, false,
            DELETE, false,
            UPLOAD, false,
            ADMIN, false,
            PUBLICATION, false,
            ANY, false
    );
    Map<String, Map<Roles,Boolean>> authorizations;
    String applicationName;
    Boolean isAdministrator;

    String userId;

    public static enum Roles{
        UPLOAD,
        DOWNLOAD,
        READ,
        ADMIN, PUBLICATION, ANY, DELETE
    }

}