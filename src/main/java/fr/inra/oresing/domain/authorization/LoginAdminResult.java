package fr.inra.oresing.domain.authorization;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public record LoginAdminResult(UUID id,
                               String login,
                               String email,
                               String state,
                               boolean authorizedForApplicationCreation,
                               boolean openAdomAdmin,
                               Set<String> authorizations,
                               Map<String, Timestamp> chartes,
                               CurrentUserRolesResult currentUserRoles) {
    public LoginAdminResult(UUID id,
                            String login,
                            String email,
                            String state,
                            CurrentUserRolesResult currentUserRoles,
                            Set<String> authorizations,
                            Map<String, Timestamp> chartes) {
        this(id,
                login,
                email,
                state,
                currentUserRoles.isApplicationCreator(),
                currentUserRoles.isOpenAdomAdmin(),
                authorizations,
                chartes,
                currentUserRoles);
    }
}