package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationManager
        permits ApplicationManagerUser, ApplicationAdminUser {
    boolean canUpdateApplication();
}
