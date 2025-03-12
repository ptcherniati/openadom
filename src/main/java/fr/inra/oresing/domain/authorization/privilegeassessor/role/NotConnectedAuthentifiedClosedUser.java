package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.rest.model.authorization.LoginAdminResult;

public record NotConnectedAuthentifiedClosedUser(LoginAdminResult loginAdminResult) implements NotConnectedUser {
}
