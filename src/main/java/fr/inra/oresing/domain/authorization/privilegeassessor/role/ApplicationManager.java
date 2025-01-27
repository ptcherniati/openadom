package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationManager
        permits ApplicationAdminUser, ApplicationDepositUser, ApplicationManagerUser {
    public static String ALL_DATANAMES = "ALL_DATANAMES";
    boolean canUpdateApplication();
}
