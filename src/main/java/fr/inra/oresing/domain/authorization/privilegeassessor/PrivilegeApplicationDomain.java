package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;

public record PrivilegeApplicationDomain(
        PrivilegeApplicationDomainEnum domain) implements PrivilegeAssessorStateApplicationDomain {
}