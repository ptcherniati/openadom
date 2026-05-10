package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.authorization.LoginAdminResult;

public record NotConnectedAuthentifiedClosedUser(LoginAdminResult loginAdminResult) implements NotConnectedUser {
}
