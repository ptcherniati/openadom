package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.HashSet;
import java.util.Set;

public class AuthorizationsForSystemUserFactory {
    CurrentUserRoles currentUserRoles;
    Set<String> applicationCreator = new HashSet<>();

    public static final AuthorizationsForSystemUserFactory builder() {
        return new AuthorizationsForSystemUserFactory();
    }

    public AuthorizationsForSystemUser build() {
        return new AuthorizationsForSystemUser(
                currentUserRoles,
                applicationCreator
        );
    }
}