package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationManager extends ApplicationPersona
        permits ApplicationAdminUser, ApplicationManagerUser {
    String ALL_DATANAMES = "ALL_DATANAMES";
   <AM extends ApplicationManager> AM canUpdateApplication();
}