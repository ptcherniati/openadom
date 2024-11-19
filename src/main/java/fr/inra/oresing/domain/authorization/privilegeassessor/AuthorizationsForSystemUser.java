package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.Set;

public record AuthorizationsForSystemUser(
        CurrentUserRoles currentUserRoles,
        Set<String> applicationCreator
) {
}
