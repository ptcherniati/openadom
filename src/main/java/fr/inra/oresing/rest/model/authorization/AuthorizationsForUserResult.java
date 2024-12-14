package fr.inra.oresing.rest.model.authorization;

import java.util.Map;

import static fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult.Roles.*;


public record AuthorizationsForUserResult(Map<String, Map<Roles, Boolean>> authorizations, String applicationName,
                                          Boolean isAdministrator, String userId) {
    public static final Map<Roles, Boolean> DEFAULT_REFERENCE_ROLES = Map.of(
            DOWNLOAD, true,
            READ, true,
            DELETE, false,
            UPLOAD, false,
            PUBLICATION, false,
            ANY, true
    );
    public static Map<Roles, Boolean> DEFAULT_ROLES = Map.of(
            DOWNLOAD, false,
            READ, false,
            DELETE, false,
            UPLOAD, false,
            PUBLICATION, false,
            ANY, false
    );

    public enum Roles {
        UPLOAD,
        DOWNLOAD,
        READ,
        PUBLICATION,
        ANY,
        APPLICATION_USER,
        ACTIVE_APPLICATION_USER,
        DELETE
    }

}