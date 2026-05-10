package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.user.CreateUserRequest;

public record NotConnectedAuthentifiedMissingPasswordUser(OreSiUser oreSiUser,
                                                          CreateUserRequest createUserRequest) implements NotConnectedUser {

}