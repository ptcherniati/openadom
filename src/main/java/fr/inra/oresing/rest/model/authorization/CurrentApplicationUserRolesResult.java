package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.List;
import java.util.UUID;

public record CurrentApplicationUserRolesResult(
        List<String> applicationRoles,
        UUID userId,
        String userLogin,
        boolean isOpenAdomAdmin,
        boolean isApplicationCreator,
        List<String> memberOf,
        boolean isDataBaseSuper
) {
    public static CurrentApplicationUserRolesResult of(CurrentUserRoles currentUserRoles, UUID applicationid) {
        return new CurrentApplicationUserRolesResult(
                currentUserRoles.applicationRoles(applicationid),
                currentUserRoles.userId(),
                currentUserRoles.userLogin(),
                currentUserRoles.isOpenAdomAdmin(),
                currentUserRoles.isApplicationCreator(),
                currentUserRoles.memberOf(),
                currentUserRoles.isDataBaseSuper()
        );
    }
}
