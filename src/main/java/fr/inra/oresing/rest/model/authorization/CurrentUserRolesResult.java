package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CurrentUserRolesResult(
        Map<String, List<String>> applicationRoles,
        UUID userId,
        String userLogin,
        boolean isOpenAdomAdmin,
        boolean isApplicationCreator,
        List<String> memberOf,
        boolean isDataBaseSuper
) {
    public static final CurrentUserRolesResult of(CurrentUserRoles currentUserRoles) {
        return new CurrentUserRolesResult(
                currentUserRoles.applicationRoles(),
                currentUserRoles.userId(),
                currentUserRoles.userLogin(),
                currentUserRoles.isOpenAdomAdmin(),
                currentUserRoles.isApplicationCreator(),
                currentUserRoles.memberOf(),
                currentUserRoles.isDataBaseSuper()
        );
    }
}
