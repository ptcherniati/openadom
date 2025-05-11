package fr.inra.oresing.domain.authorization.privilegeassessor;

sealed public interface PrivilegeAssessorStateSystemDomain extends PrivilegeAssessorStateDomain
        permits PrivilegeSystemDomain {
}