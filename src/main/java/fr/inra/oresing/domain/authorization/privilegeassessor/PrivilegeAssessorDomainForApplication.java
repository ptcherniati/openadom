package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserReaderRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationAdminUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManager;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManagerUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationReader;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.*;
import java.util.stream.Collectors;

public record PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomain>(
        AuthorizationsForApplicationUser authorizations,
        PrivilegeApplicationDomain domain,
        Application application,
        GetGrantableResult grantable) implements PrivilegeAssessorDomain {
    public ApplicationManager forUpdateApplication() {
        if (!authorizations.isApplicationManager()) {
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }

    public ApplicationManager forManageAuthorizations() {
        if (!authorizations.isUserManager()) {
            throw new NotApplicationUserManagerRightsException(application.getName());
        }
        return new ApplicationManagerUser();
    }

    public ApplicationManager forAddAuthorization() {
        if (!authorizations.isUserManager()) {
            throw new NotApplicationUserReaderRightsException(application.getName());
        }
        return new ApplicationManagerUser();
    }

    public ApplicationAdminUser forManageAdministrator() {
        if (!authorizations.isApplicationManager()) {
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

    public Map<AuthorizationsForUserResult.Roles, Boolean> getAuthorizationsForUser(String dataName) {
        Map<AuthorizationsForUserResult.Roles, Boolean> roleForDatatype = new HashMap<>();

        Set<OperationType> rolesSetted = Optional.ofNullable(authorizations().userAuthorizations())
                .map(map -> map.get(dataName))
                .filter(obj -> true)
                .map(authList -> authList.stream()
                        .flatMap(auth -> auth.operationTypes().stream())
                        .collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        Optional.ofNullable(authorizations().publicAuthorizations())
                .map(map -> map.get(dataName))
                .map(AuthorizationParsed::operationTypes)
                .ifPresent(rolesSetted::addAll
                );


        boolean isAdministrator = authorizations().isApplicationManager() || authorizations().isUserManager();
        roleForDatatype.put(AuthorizationsForUserResult.Roles.UPLOAD, isAdministrator || rolesSetted.contains(OperationType.depot) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DELETE, isAdministrator || rolesSetted.contains(OperationType.delete));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DOWNLOAD, isAdministrator || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication) || rolesSetted.contains(OperationType.delete));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.READ, isAdministrator || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication) || rolesSetted.contains(OperationType.delete));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.PUBLICATION, isAdministrator || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.APPLICATION_USER, isAdministrator || grantable().authorizationsForUser().applicationUser());
        roleForDatatype.put(AuthorizationsForUserResult.Roles.ACTIVE_APPLICATION_USER, isAdministrator || grantable().authorizationsForUser().activeApplicationUser());
        roleForDatatype.put(AuthorizationsForUserResult.Roles.ANY, isAdministrator || !rolesSetted.isEmpty());
        return roleForDatatype;
    }

    public ApplicationAdminUser forDeleteAuthorization() {
        if(!authorizations().isApplicationManager()) {
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return  new ApplicationAdminUser(application());
    }
}
