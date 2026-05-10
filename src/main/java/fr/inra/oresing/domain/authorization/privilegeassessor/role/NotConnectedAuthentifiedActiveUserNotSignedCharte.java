package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.user.CreateUserRequest;

public record NotConnectedAuthentifiedActiveUserNotSignedCharte(
        OreSiUser user,
        CreateUserRequest createUserRequest,
        String charte) implements NotConnectedUser {
}
