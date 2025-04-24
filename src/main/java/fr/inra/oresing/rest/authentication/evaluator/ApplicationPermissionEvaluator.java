package fr.inra.oresing.rest.authentication.evaluator;

import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplication;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.services.authorization.AuthorizationService;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class ApplicationPermissionEvaluator implements PermissionEvaluator {
    public static final String SYSTEM = "SYSTEM";
    public static final String SYSTEM_USER = "SYSTEM_USER";
    public static final String SYSTEM_OPENADOM_ADMIN = "SYSTEM_OPENADOM_ADMIN";
    public static final String SYSTEM_APPLICATION_CREATE = "SYSTEM_APPLICATION_CREATE";
    public static final String SYSTEM_APPLICATION_CREATOR = "SYSTEM_APPLICATION_CREATOR";
    public static final String SYSTEM_MANAGE_ROLE_FOR_UPDATE = "SYSTEM_MANAGE_ROLE_FOR_UPDATE";
    public static final String SYSTEM_MANAGE_ROLE_FOR_DELETE = "SYSTEM_MANAGE_ROLE_FOR_DELETE";
    public static final String SYSTEM_USER_READER = "SYSTEM_USER_READER";

    public static final String APPLICATION = "APPLICATION";
    public static final String APPLICATION_APPLICATION_MODIFY = "APPLICATION_APPLICATION_MODIFY";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ";
    public static final String APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE = "APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE";
    public static final String APPLICATION_ROLE_MANAGEMENT_FOR_DELETE = "APPLICATION_ROLE_MANAGEMENT_FOR_DELETE";
    public static final String APPLICATION_DATA_READ = "APPLICATION_DATA_READ";
    public static final String APPLICATION_DATA_WRITE = "APPLICATION_DATA_WRITE";
    public static final String APPLICATION_WRITE_FILE = "APPLICATION_WRITE_FILE";
    public static final String APPLICATION_DELETE_FILE = "APPLICATION_DELETE_FILE";

    public Supplier<PrivilegeAssessorDomainForSystem> SYSTEM_USER_CONNECTED;
    public Supplier<PrivilegeAssessorDomainForSystem> SYSTEM_ADMINISTRATION;

    public Function<String, PrivilegeAssessorDomainForApplication> APPLICATION_MANAGER;
    public Function<String, PrivilegeAssessorDomainForApplication> DATA_MANAGEMENT;
    public Function<String, PrivilegeAssessorDomainForApplication> DATA_READ;
    public Function<String, PrivilegeAssessorDomainForApplication> DATA_WRITE;
    public Function<String, PrivilegeAssessorDomainForApplication> DATA_ACCESS;


    private final AuthorizationService authorizationService;

    public ApplicationPermissionEvaluator(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
        this.SYSTEM_USER_CONNECTED = () -> authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_USER_CONNECTED);
        this.SYSTEM_ADMINISTRATION = () -> authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_ADMINISTRATION);

        this.APPLICATION_MANAGER = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.APPLICATION_MANAGER, applicationName);
        this.DATA_MANAGEMENT = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_MANAGEMENT, applicationName);
        this.DATA_READ = applicationName -> authorizationService

                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_READ, applicationName);
        this.DATA_WRITE = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_WRITE, applicationName);
        this.DATA_ACCESS = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_ACCESS, applicationName);
    }

    @Override
    /*
    placement des permissions dans le fichier de configuration :
     */
    public boolean hasPermission(Authentication authentication, Object targetDomain, Object permission) {
        Optional<OreSiAuthenticationToken> oreSiAuthenticationTokenOpt = Optional.ofNullable(authentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast);
        if (SYSTEM.equals(targetDomain)) {
            return hasPermissionForSystem(oreSiAuthenticationTokenOpt, permission);
        } else if (APPLICATION.equals(targetDomain)) {
            return hasPermissionForApplication(oreSiAuthenticationTokenOpt, permission);
        }
        return false;
    }

    private boolean hasPermissionForSystem(Optional<OreSiAuthenticationToken> oreSiAuthenticationTokenOpt, Object permission) {
        return oreSiAuthenticationTokenOpt
                .map(oreSiAuthenticationToken -> {
                    SystemPersona persona = switch (permission) {
                        case String roleApplicationCreate when SYSTEM_APPLICATION_CREATE.equals(roleApplicationCreate) ->
                                SYSTEM_USER_CONNECTED.get()
                                        .forCreateApplication();
                        case String roleDelete when SYSTEM_MANAGE_ROLE_FOR_DELETE.equals(roleDelete) ->
                                SYSTEM_USER_CONNECTED.get()
                                        .forAdministrationManagement();
                        case String roleUpdate when SYSTEM_MANAGE_ROLE_FOR_UPDATE.equals(roleUpdate) ->
                                SYSTEM_USER_CONNECTED.get()
                                        .forAdministrationManagement();
                        case String connectedUser when SYSTEM_USER.equals(connectedUser) -> SYSTEM_USER_CONNECTED.get()
                                .connectedUser();
                        case String openAdomAdmin when SYSTEM_OPENADOM_ADMIN.equals(openAdomAdmin) ->
                                SYSTEM_ADMINISTRATION.get()
                                        .forAdministrationManagement();
                        case String applicationCreator when SYSTEM_APPLICATION_CREATOR.equals(applicationCreator) ->
                                SYSTEM_ADMINISTRATION.get()
                                        .forCreateApplication();
                        case String userReader when SYSTEM_USER_READER.equals(userReader) -> SYSTEM_USER_CONNECTED.get()
                                .forAdministrationManagement();
                        default -> null;
                    };
                    if (persona != null) {
                        oreSiAuthenticationToken.setSystemPersona(persona);
                        return true;
                    }
                    return false;

                })
                .orElse(false);
    }

    private boolean hasPermissionForApplication(Optional<OreSiAuthenticationToken> oreSiAuthenticationTokenOpt, Object permission) {
        Optional<String> applicationNameOpt = oreSiAuthenticationTokenOpt
                .map(OreSiAuthenticationToken::getApplicationName);
        if (applicationNameOpt.isEmpty()) {
            return false;
        }
        Optional<String> dataNameOpt = oreSiAuthenticationTokenOpt
                .map(OreSiAuthenticationToken::getDataName);
        return applicationNameOpt
                .flatMap(applicationName -> {
                    return switch (permission) {
                        case String applicationModify when APPLICATION_APPLICATION_MODIFY.equals(applicationModify) ->
                                Optional.of(APPLICATION_MANAGER.apply(applicationName)
                                        .forUpdateApplication());
                        case String applicationDeleteRole when APPLICATION_ROLE_MANAGEMENT_FOR_DELETE.equals(applicationDeleteRole) ->
                                Optional.of(APPLICATION_MANAGER.apply(applicationName)
                                        .forManageAdministrator());
                        case String applicationUpdateRole when APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE.equals(applicationUpdateRole) ->
                                Optional.of(APPLICATION_MANAGER.apply(applicationName)
                                        .forManageAdministrator());
                        case String applicationAdminForRead when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ.equals(applicationAdminForRead) ->
                                Optional.of(DATA_MANAGEMENT.apply(applicationName)
                                        .forManageAuthorizations());
                        case String applicationAdminForDelete when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE.equals(applicationAdminForDelete) ->
                                Optional.of(DATA_MANAGEMENT.apply(applicationName)
                                        .forDeleteAuthorization());
                        case String applicationAdminForUpdate when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE.equals(applicationAdminForUpdate) ->
                                Optional.of(DATA_MANAGEMENT.apply(applicationName)
                                        .forManageAuthorizations());
                        case String applicationAdminForAdd when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD.equals(applicationAdminForAdd) ->
                                Optional.of(DATA_MANAGEMENT.apply(applicationName)
                                        .forManageAuthorizations());
                        case String read when APPLICATION_DATA_READ.equals(read) -> dataNameOpt
                                .map(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_READ, applicationName)
                                        ::forDataRead);
                        case String read when APPLICATION_DATA_WRITE.equals(read) -> dataNameOpt
                                .map(dataName ->
                                        OreSiApiRequestContext.getAuthentication()
                                                .map(OreSiAuthenticationToken::getFileOrUUID)
                                                .map(FileOrUUID::topublish)
                                                .or(() -> Optional.of(false))
                                                .map(toPublish -> authorizationService
                                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_READ, applicationName)
                                                        .forDataWrite(dataName, toPublish))
                                                .orElse(null)
                                );
                        case String writeFile when APPLICATION_WRITE_FILE.equals(writeFile) -> dataNameOpt
                                .map(dataName -> DATA_WRITE.apply(applicationName)
                                        .forDataWrite(dataName, false));
                        case String deleteFile when APPLICATION_DELETE_FILE.equals(deleteFile) -> dataNameOpt
                                .map(dataName -> DATA_READ.apply(applicationName)
                                        .forDataDelete(dataName));
                        default -> Optional.empty();
                    };
                })
                .map(applicationPersona -> {
                    oreSiAuthenticationTokenOpt
                            .ifPresent(oreSiAuthenticationToken -> oreSiAuthenticationToken.setApplicationPersonna(applicationPersona));
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object
            permission) {
        return false;
    }
}