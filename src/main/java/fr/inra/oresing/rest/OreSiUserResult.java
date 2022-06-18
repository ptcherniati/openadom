package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class
OreSiUserResult extends OreSiUser {
    private CurrentUserRoles roles;

    public OreSiUserResult(OreSiUser user, CurrentUserRoles userRoles) {
        super();
        setLogin(user.getLogin());
        setAuthorizations(user.getAuthorizations());
        setId(user.getId());
        setRoles(userRoles);
    }
}