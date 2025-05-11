package fr.inra.oresing.domain.authorization.privilegeassessor;


public sealed interface PrivilegeAssessorStateDomain extends PrivilegeAssessorState permits PrivilegeAssessorStateApplicationDomain, PrivilegeAssessorStateSystemDomain {

}