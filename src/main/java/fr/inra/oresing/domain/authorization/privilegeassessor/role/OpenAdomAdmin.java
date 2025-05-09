package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalRoleToBeGranted;

import java.util.Set;

public record OpenAdomAdmin() implements ApplicationCreator {
    public static final String OPEN_ADOM_ADMIN_ROLE = "openAdomAdmin";
    @Override
    public boolean canCreateApplication(String applicationName) {
        return true;
    }

    public void canManagerRightForRole(OreSiRoleForUser roleForUser) {
        if(!Set.of(OPEN_ADOM_ADMIN_ROLE, ApplicationCreatorUser.APPLICATION_CREATOR_ROLE).contains(roleForUser.role())){
            throw new IllegalRoleToBeGranted(roleForUser.role());
        }
    }
}