package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class
OreSiUserResult extends OreSiUser {
    private CurrentUserRoles roles;

    public OreSiUserResult(final OreSiUser user, final CurrentUserRoles userRoles) {
        setLogin(user.getLogin());
        setAuthorizations(user.getAuthorizations());
        setId(user.getId());
        setRoles(userRoles);
    }
}