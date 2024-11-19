package fr.inra.oresing.domain.authorization.privilegeassessor;


public sealed interface PrivilegeAssessorStateApplicationDomain
        extends PrivilegeAssessorStateDomain
        permits PrivilegeApplicationDomain {
}
