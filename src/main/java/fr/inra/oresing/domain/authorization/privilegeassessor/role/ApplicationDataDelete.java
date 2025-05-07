package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationDataDelete extends ApplicationDataWriter
        permits ApplicationAdminUser, ApplicationDeleteUser, ApplicationManagerUser {
}