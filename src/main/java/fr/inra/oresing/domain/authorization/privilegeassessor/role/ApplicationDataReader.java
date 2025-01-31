package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalRoleToBeGranted;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.IllegalUserToBeGranted;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ApplicationDataReader(Application application) implements ApplicationUser {
}
