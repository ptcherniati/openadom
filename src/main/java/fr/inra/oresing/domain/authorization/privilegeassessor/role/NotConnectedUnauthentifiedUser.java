package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.user.CreateUserRequest;

public record NotConnectedUnauthentifiedUser(CreateUserRequest createUserRequest) implements NotConnectedUser {
}