package fr.inra.oresing.domain.services.authorization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplication;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

public interface AuthorizationService {
    AuthorizationsResult getAuthorizationsForUserAndPublic(String applicationName, String currentUser);

    PrivilegeAssessorDomainForSystem getPrivilegeAssessorForSystem(
            PrivilegeSystemDomain privilegeDomain
    );

    PrivilegeAssessorDomainForApplication getPrivilegeAssessorForApplication(
            PrivilegeApplicationDomain privilegeDomain,
            Application application
    );
}
