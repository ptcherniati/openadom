package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdministratorForSystemException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Set;

public sealed interface PrivilegeAssessorBuilder<PrivilegeAssessorState>
        permits PrivilegeAssessorDomain {


    static PrivilegeAssessorDomainForSystem<PrivilegeAssessorStateDomain.PrivilegeAssessorStateSystemDomain> forSystem(
            AuthorizationsForSystemUser authorizations,
            PrivilegeSystemDomain privilegeDomain) {
        boolean isOpenAdomAdmin = authorizations.currentUserRoles().isOpenAdomAdmin();
        Set<String> applicationCreatorRegexp = authorizations.applicationCreator();
        if (!isOpenAdomAdmin && CollectionUtils.isEmpty(applicationCreatorRegexp)) {
            throw new NotOpenAdomAdministratorForSystemException();
        }
        return new PrivilegeAssessorDomainForSystem(
                authorizations,
                privilegeDomain
        );
    }

    static PrivilegeAssessorDomainForApplication<PrivilegeAssessorStateApplicationDomain> forApplication(
            AuthorizationsForApplicationUser authorizations,
            PrivilegeApplicationDomain privilegeDomain,
            Application application,
            GetGrantableResult grantable) {
        return new PrivilegeAssessorDomainForApplication(
                authorizations,
                privilegeDomain,
                application,
                grantable);
    }


}