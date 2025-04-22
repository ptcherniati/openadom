package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;

public sealed interface ApplicationUser extends ApplicationPersona
        permits ApplicationDataReader, ApplicationDataWriter {
}
