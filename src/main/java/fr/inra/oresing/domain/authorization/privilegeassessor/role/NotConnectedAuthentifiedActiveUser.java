package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.user.CreateUserRequest;

public record NotConnectedAuthentifiedActiveUser(OreSiUser user,
                                                 CreateUserRequest createUserRequest) implements NotConnectedUser {

}