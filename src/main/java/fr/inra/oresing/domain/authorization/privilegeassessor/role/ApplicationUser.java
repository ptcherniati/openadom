package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationUser extends ApplicationPersona
        permits ApplicationDataReaderUser, ApplicationDataWriter {
}