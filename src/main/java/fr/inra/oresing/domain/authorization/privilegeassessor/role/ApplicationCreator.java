package fr.inra.oresing.domain.authorization.privilegeassessor.role;

public sealed interface ApplicationCreator extends SystemPersona
        permits  ApplicationCreatorUser, OpenAdomAdmin {
    void canCreateApplication(String applicationName);
}