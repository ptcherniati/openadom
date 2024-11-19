package fr.inra.oresing.domain.authorization.privilegeassessor;

public sealed interface PrivilegeAssessorDomain<PrivilegeAssessorStateDomain>
        extends PrivilegeAssessorBuilder<PrivilegeAssessorStateDomain>
        permits PrivilegeAssessorDomainForApplication,
        PrivilegeAssessorDomainForSystem {

}
