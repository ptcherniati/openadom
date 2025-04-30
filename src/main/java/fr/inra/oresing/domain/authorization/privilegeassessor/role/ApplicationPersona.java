package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;

public sealed interface ApplicationPersona permits ApplicationUser, ApplicationManager {
    Application application();
}
