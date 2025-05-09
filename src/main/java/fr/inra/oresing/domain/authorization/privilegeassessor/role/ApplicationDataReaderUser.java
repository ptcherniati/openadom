package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;

public record ApplicationDataReaderUser(Application application) implements ApplicationUser {
}