package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationUser
        permits ApplicationDataReader, ApplicationDataWriter {
}
