package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserReaderRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationAdminUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManager;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManagerUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationReader;

import java.util.List;
import java.util.Optional;

public record PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomain>(
        AuthorizationsForApplicationUser authorizations,
        PrivilegeApplicationDomain domain,
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
            throw new NotApplicationUserReaderRightsException(application.getName());
        }
        return new ApplicationManagerUser();
    }

    public ApplicationAdminUser forManageAdministrator() {
        if(!authorizations.isApplicationManager()){
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }

    public ApplicationReader forDataRead(String dataName) {
        Optional.of(authorizations())
                .filter(authorizationsForApplicationUser -> authorizationsForApplicationUser.canRead(dataName))
                .orElseThrow(NotApplicationManagerRightsException::new);
    return new ApplicationReader(application());
    }
}
