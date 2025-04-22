package fr.inra.oresing.rest.authentication.evaluator;

import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.services.authorization.AuthorizationService;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.Optional;

public class ApplicationPermissionEvaluator implements PermissionEvaluator {
    public static final String SYSTEM = "SYSTEM";
    public static final String SYSTEM_USER = "SYSTEM_USER";
    public static final String SYSTEM_OPENADOM_ADMIN = "SYSTEM_OPENADOM_ADMIN";
    public static final String SYSTEM_APPLICATION_CREATOR = "SYSTEM_APPLICATION_CREATOR";
    public static final String SYSTEM_MANAGE_ROLE_FOR_UPDATE = "SYSTEM_MANAGE_ROLE_FOR_UPDATE";
    public static final String SYSTEM_MANAGE_ROLE_FOR_DELETE = "SYSTEM_MANAGE_ROLE_FOR_DELETE";

    public static final String APPLICATION = "APPLICATION";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE";
    public static final String APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ = "APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ";
    public static final String APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE = "APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE";
    public static final String APPLICATION_ROLE_MANAGEMENT_FOR_DELETE = "APPLICATION_ROLE_MANAGEMENT_FOR_DELETE";
    public static final String APPLICATION_READ = "APPLICATION_READ";
    public static final String APPLICATION_WRITE_FILE = "APPLICATION_WRITE_FILE";
    public static final String APPLICATION_WRITE_PUBLISH = "APPLICATION_WRITE_PUBLISH";
    public static final String APPLICATION_DELETE_FILE = "APPLICATION_DELETE_FILE";


    private final AuthorizationService authorizationService;

    public ApplicationPermissionEvaluator(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
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
                        case String roleDelete when SYSTEM_MANAGE_ROLE_FOR_DELETE.equals(roleDelete) ->
                                authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_USER_CONNECTED)
                                        .forAdministrationManagement();
                        case String roleUpdate when SYSTEM_MANAGE_ROLE_FOR_UPDATE.equals(roleUpdate) ->
                                authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_USER_CONNECTED)
                                        .forAdministrationManagement();
                        case String connectedUser when SYSTEM_USER.equals(connectedUser) ->
                                authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_USER_CONNECTED)
                                        .connectedUser();
                        case String openAdomAdmin when SYSTEM_OPENADOM_ADMIN.equals(openAdomAdmin) ->
                                authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_ADMINISTRATION)
                                        .forAdministrationManagement();
                        case String applicationCreator when SYSTEM_APPLICATION_CREATOR.equals(applicationCreator) ->
                                authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_ADMINISTRATION)
                                        .forCreateApplication();
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
                        case String applicationDeleteRole when APPLICATION_ROLE_MANAGEMENT_FOR_DELETE.equals(applicationDeleteRole) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.APPLICATION_MANAGER, applicationName)
                                        .forManageAdministrator());
                        case String applicationUpdateRole when APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE.equals(applicationUpdateRole) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.APPLICATION_MANAGER, applicationName)
                                        .forManageAdministrator());
                        case String applicationAdminForRead when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ.equals(applicationAdminForRead) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_MANAGEMENT, applicationName)
                                        .forManageAuthorizations());
                        case String applicationAdminForDelete when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE.equals(applicationAdminForDelete) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_MANAGEMENT, applicationName)
                                        .forDeleteAuthorization());
                        case String applicationAdminForUpdate when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE.equals(applicationAdminForUpdate) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_MANAGEMENT, applicationName)
                                        .forManageAuthorizations());
                        case String applicationAdminForAdd when APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD.equals(applicationAdminForAdd) ->
                                Optional.of(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_MANAGEMENT, applicationName)
                                        .forManageAuthorizations());
                        case String read when APPLICATION_READ.equals(read) -> dataNameOpt
                                .map(authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_READ, applicationName)
                                        ::forDataRead);
                        case String writeFile when APPLICATION_WRITE_FILE.equals(writeFile) -> dataNameOpt
                                .map(dataName -> authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_WRITE, applicationName)
                                        .forDataWrite(dataName, false));
                        case String writePublish when APPLICATION_WRITE_PUBLISH.equals(writePublish) -> dataNameOpt
                                .map(dataName -> authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_WRITE, applicationName)
                                        .forDataWrite(dataName, true));
                        case String deleteFile when APPLICATION_DELETE_FILE.equals(deleteFile) -> dataNameOpt
                                .map(dataName -> authorizationService
                                        .getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_READ, applicationName)
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