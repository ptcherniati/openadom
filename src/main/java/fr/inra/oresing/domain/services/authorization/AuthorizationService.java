package fr.inra.oresing.domain.services.authorization;

import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplication;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.authorization.AuthorizationsResult;

public interface AuthorizationService {
    AuthorizationsResult getAuthorizationsForUserAndPublic(String applicationName, String currentUser);

    PrivilegeAssessorDomainForSystem getPrivilegeAssessorForSystem(
            PrivilegeSystemDomainEnum privilegeDomain
    );

    PrivilegeAssessorDomainForApplication getPrivilegeAssessorForApplication(
            PrivilegeApplicationDomainEnum privilegeDomain,
            String applicationNameOrId
    );
}