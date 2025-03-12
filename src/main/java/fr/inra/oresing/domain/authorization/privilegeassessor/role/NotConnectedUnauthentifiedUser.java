package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public record NotConnectedUnauthentifiedUser(fr.inra.oresing.rest.CreateUserRequest createUserRequest) implements NotConnectedUser {
}
