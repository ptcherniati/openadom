package fr.inra.oresing.domain.authorization;

import java.util.Map;


public record AuthorizationsForUserResult(Map<String, Map<Roles, Boolean>> authorizations, String applicationName,
                                          Boolean isAdministrator, String userId) {

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