package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiUser;

public record NotConnectedAuthentifiedPendingUser(OreSiUser user) implements NotConnectedUser {

}
