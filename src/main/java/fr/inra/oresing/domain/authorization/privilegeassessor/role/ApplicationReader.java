package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalRoleToBeGranted;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalUserToBeGranted;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;

import java.util.List;
import java.util.Optional;

public record ApplicationReader(Application application) implements ApplicationUser {
    @Override
    public boolean canUpdateApplication() {
        return true;
    }

    public boolean canManagerRightOfUserForRole(OreSiUser user, OreSiRoleForUser roleForUser) {
        if (Optional.ofNullable(user)
                .map(OreSiUser::getChartes)
                .map(chartes -> chartes.get(application().getId().toString()))
                .isEmpty()) {
            throw new IllegalUserToBeGranted(user, application().getName());
        }
        OreSiRightOnApplicationRole userManager = OreSiRightOnApplicationRole.userAdminOn(application());
        OreSiRightOnApplicationRole applicationManager = OreSiRightOnApplicationRole.adminOn(application());
        if (!List.of(applicationManager.getAsSqlRole(), userManager.getAsSqlRole()).toString().contains(roleForUser.role())) {
            throw new IllegalRoleToBeGranted(roleForUser.role());
        }
        return true;
    }
}
