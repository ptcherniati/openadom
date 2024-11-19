package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationCreator
        permits  ApplicationCreatorUser, OpenAdomAdmin {
    boolean canCreateApplication(String applicationName);
}
