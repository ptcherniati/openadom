package fr.inra.oresing.domain.repository.user.file;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.Optional;

public interface UserRepository {
    Optional<OreSiUser> findByLogin(String login);
    Optional<OreSiUser> findByLoginOrId(String loginOrId);
    CurrentUserRoles getRolesForCurrentUser();
}
