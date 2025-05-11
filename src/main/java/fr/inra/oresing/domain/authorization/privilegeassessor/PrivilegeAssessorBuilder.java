package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdministratorForSystemException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.repository.user.file.UserRepository;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Set;

public sealed interface PrivilegeAssessorBuilder<PrivilegeAssessorState>
        permits PrivilegeAssessorDomain {


    static PrivilegeAssessorDomainForSystem<PrivilegeSystemDomainEnum> forSystem(
            AuthorizationsForSystemUser authorizations,
            PrivilegeSystemDomainEnum privilegeSystemDomainEnum) {
        boolean isOpenAdomAdmin = authorizations.currentUserRoles().isOpenAdomAdmin();
        Set<String> applicationCreatorRegexp = authorizations.applicationCreator();
        if (!isOpenAdomAdmin && CollectionUtils.isEmpty(applicationCreatorRegexp)) {
            throw new NotOpenAdomAdministratorForSystemException();
        }
        return new PrivilegeAssessorDomainForSystem<>(
                authorizations,
                privilegeSystemDomainEnum
        );
    }

    static PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum> forApplication(
            AuthorizationsForApplicationUser authorizations,
            PrivilegeApplicationDomainEnum privilegeApplicationDomainEnum,
            Application application,
            GetGrantableResult grantable) {
        return new PrivilegeAssessorDomainForApplication<>(
                authorizations,
                privilegeApplicationDomainEnum,
                application,
                grantable);
    }


    static PrivilegeAssessorDomainForSystem<PrivilegeSystemDomainEnum> forUser(
            AuthorizationsForSystemUser authorizations,
            PrivilegeSystemDomainEnum privilegeSystemDomainEnum) {
        return new PrivilegeAssessorDomainForSystem<>(
                authorizations,
                privilegeSystemDomainEnum
        );
    }

    static PrivilegeAssessorDomainForNotConnectedUser<PrivilegeSystemDomainEnum> forNotConnectedUser(AuthenticationServiceImpl authenticationService, UserRepository userRepository, PrivilegeSystemDomainEnum privilegeSystemDomainEnum) {
        return new PrivilegeAssessorDomainForNotConnectedUser<>(
                authenticationService,
                userRepository,
                privilegeSystemDomainEnum
        );
    }
}