package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.*;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.*;
import java.util.stream.Collectors;

import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserReaderRightsException.NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION;

public record PrivilegeAssessorDomainForApplication<P extends PrivilegeApplicationDomainEnum>(
        AuthorizationsForApplicationUser authorizations,
        P domain,
        Application application,
        GetGrantableResult grantable) implements PrivilegeAssessorDomain {
    /*
    Test if is applicationAdminUserForUpdate
     */
    public ApplicationManager forUpdateApplication() {
        if (!authorizations.isApplicationManager()) {
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }

    /*
    Test if is applicationManagerUserForUpdateRights
     */
    public ApplicationManager forManageAuthorizations() {
        if (!(authorizations.isUserManager() || authorizations().isApplicationManager())) {
            throw new NotApplicationUserManagerRightsException(application.getName());
        }
        return new ApplicationManagerUser(application());
    }


    /*
    Test if is applicationManagerUserForCreateRights
     */
    public ApplicationManager forAddAuthorization() {
        if (!authorizations.isUserManager()) {
            throw new NotApplicationUserReaderRightsException(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION);
        }
        return new ApplicationManagerUser(application());
    }

    /*
    Test if is applicationManagerUserForManageAdministrator
     */
    public ApplicationAdminUser forManageAdministrator() {
        if (!authorizations.isApplicationManager()) {
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }


    /*
    Test if is applicationUserForReadingData
     */
    public ApplicationDataReaderUser forDataRead(String dataName) {
        if (Optional.of(authorizations())
                .filter(authorizationsForApplicationUser -> authorizationsForApplicationUser.canRead(dataName))
                .isEmpty()) {
            throw new NotApplicationDataReaderException(application().getName(), dataName);
        }
        return new ApplicationDataReaderUser(application());
    }
    public ApplicationDataReaderUser forDataReadSome() {
        if(authorizations().isApplicationManager() || authorizations().isUserManager()) {
            return new ApplicationDataReaderUser(application());
        }
        if (Optional.of(authorizations())
                .map(AuthorizationsForApplicationUser::roles)
                .stream()
                .flatMap(List::stream)
                .noneMatch("writer"::equals)) {
            throw new NotApplicationDataReaderException(application().getName());
        }
        return new ApplicationDataReaderUser(application());
    }

    public Map<AuthorizationsForUserResult.Roles, Boolean> getAuthorizationsForUser(String dataName) {
        Map<AuthorizationsForUserResult.Roles, Boolean> roleForDatatype = new HashMap<>();

        Set<OperationType> rolesSetted = Optional.ofNullable(authorizations().userAuthorizations())
                .map(map -> map.get(dataName))
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
        if (!authorizations().isApplicationManager()) {
            throw new NotApplicationManagerRightsException(application.getName());
        }
        return new ApplicationAdminUser(application());
    }

    public ApplicationDataWriter forDataWrite(String dataName, boolean toPublish) {
        AuthorizationsForApplicationUser authorizationsForApplicationUser = Optional.of(authorizations())
                .filter(authorizations -> authorizations.canWrite(dataName, toPublish))
                .orElseThrow(() -> new NotApplicationDataWriterException(application().getName(), dataName));
        if (authorizationsForApplicationUser.isApplicationManager()) {
            return new ApplicationAdminUser(application(), dataName);
        }
        if (authorizationsForApplicationUser.isUserManager()) {
            return new ApplicationManagerUser(application(), dataName);
        }
        if (toPublish || !application().isData(dataName)) {
            return new ApplicationPublishWriterUser(
                    application(),
                    dataName,
                    authorizationsForApplicationUser.getAuthorizations(dataName, application().isData(dataName)?Set.of(OperationType.publication):Set.of(OperationType.publication, OperationType.depot))
            );
        }
        return new ApplicationDepositWriterUser(
                application(),
                dataName,
                authorizationsForApplicationUser.getAuthorizations(dataName, Set.of(OperationType.depot))
        );
    }

    public ApplicationDataDelete forDataDelete(String dataName) {
        boolean isRepository = application().findSubmission(dataName)
                .map(Submission::strategy)
                .filter(SubmissionType.OA_VERSIONING::equals)
                .isPresent();
        AuthorizationsForApplicationUser authorizationsForApplicationUser = Optional.of(authorizations())
                .filter(authorizations -> authorizations.canDelete(dataName, isRepository))
                .orElseThrow(() -> new NotApplicationCanDeleteRightsException(application().getName(), dataName));
        if (authorizationsForApplicationUser.isApplicationManager()) {
            return new ApplicationAdminUser(application(), dataName);
        }
        if (authorizationsForApplicationUser.isUserManager()) {
            return new ApplicationManagerUser(application(), dataName);
        }
        return new ApplicationDeleteUser(
                application(),
                dataName,
                authorizationsForApplicationUser.getAuthorizations(dataName, Set.of(OperationType.delete))
        );
    }

}