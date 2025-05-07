package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;

public record ApplicationDataReader(Application application) implements ApplicationUser {
}