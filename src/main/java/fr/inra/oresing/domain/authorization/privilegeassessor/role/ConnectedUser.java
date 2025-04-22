package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.Set;

public record ConnectedUser(CurrentUserRoles roles, Set<String> applicationCreator) implements SystemPersona {
    public String getLogin(){
        return roles().userLogin();
    }
}
