package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.rest.CreateUserRequest;

public record NotConnectedUnauthentifiedUser(CreateUserRequest createUserRequest) implements NotConnectedUser {
}