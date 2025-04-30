package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.CreateUserRequest;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public record NotConnectedAuthentifiedMissingPasswordUser(OreSiUser oreSiUser, CreateUserRequest createUserRequest) implements NotConnectedUser {

}
