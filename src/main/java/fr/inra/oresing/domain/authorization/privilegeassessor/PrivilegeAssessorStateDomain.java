package fr.inra.oresing.domain.authorization.privilegeassessor;


public sealed interface PrivilegeAssessorStateDomain<PrivilegeDomain> extends PrivilegeAssessorState permits PrivilegeAssessorStateApplicationDomain, PrivilegeAssessorStateDomain.PrivilegeAssessorStateSystemDomain {

    public sealed interface PrivilegeAssessorStateSystemDomain extends fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorStateDomain
            permits PrivilegeSystemDomain {
    }

}
