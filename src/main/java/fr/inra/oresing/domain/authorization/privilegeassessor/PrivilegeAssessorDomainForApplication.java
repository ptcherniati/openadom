package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationAdminUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManager;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManagerUser;

import java.util.List;

public record PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomain>(
        AuthorizationsForApplicationUser authorizations, fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain domain,
        Application application) implements PrivilegeAssessorDomain {
    public ApplicationManager forUpdateApplication() {
        if(!authorizations.isApplicationManager()){
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }

    public ApplicationManager forManageAuthorizations() {
        if(!authorizations.isUserManager()){
            throw new NotApplicationUserManagerRightsException(application.getName());
        }
        return new ApplicationManagerUser();
    }

    public ApplicationManager forAddAuthorization() {
        if(!authorizations.isUserManager()){
            throw new NotApplicationUserManagerRightsException(application.getName());
        }
        return new ApplicationManagerUser();
    }

    public ApplicationAdminUser forManageAdministrator() {
        if(!authorizations.isApplicationManager()){
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }
}
