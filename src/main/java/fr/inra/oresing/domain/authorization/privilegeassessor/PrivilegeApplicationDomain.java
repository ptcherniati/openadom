package fr.inra.oresing.domain.authorization.privilegeassessor;

public record PrivilegeApplicationDomain(
        fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain domain) implements PrivilegeAssessorStateApplicationDomain {
}
