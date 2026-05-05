package fr.inra.oresing.rest.authentication.evaluator;

import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplication;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.SystemPersona;
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
    public static final String APPLICATION_DATA_READ_SOME = "APPLICATION_DATA_READ_SOME";
    public static final String APPLICATION_DATA_WRITE = "APPLICATION_DATA_WRITE";
    public static final String APPLICATION_WRITE_FILE = "APPLICATION_WRITE_FILE";
    public static final String APPLICATION_DELETE_FILE = "APPLICATION_DELETE_FILE";
    public static final String APPLICATION_DATA_DOWNLOAD_BUNDLE = "APPLICATION_DATA_DOWNLOAD_BUNDLE";
    private final AuthorizationService authorizationService;
    public Supplier<PrivilegeAssessorDomainForSystem<PrivilegeSystemDomainEnum>> SYSTEM_USER_CONNECTED;
    public Supplier<PrivilegeAssessorDomainForSystem<PrivilegeSystemDomainEnum>> SYSTEM_ADMINISTRATION;
    public Function<String, PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum>> APPLICATION_MANAGER;
    public Function<String, PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum>> DATA_MANAGEMENT;
    public Function<String, PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum>> DATA_READ;
    public Function<String, PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum>> DATA_WRITE;
    public Function<String, PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum>> DATA_ACCESS;

    public ApplicationPermissionEvaluator(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
        this.SYSTEM_USER_CONNECTED = () -> authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomainEnum.SYSTEM_USER_CONNECTED);
        this.SYSTEM_ADMINISTRATION = () -> authorizationService.getPrivilegeAssessorForSystem(PrivilegeSystemDomainEnum.SYSTEM_ADMINISTRATION);

        this.APPLICATION_MANAGER = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.APPLICATION_MANAGER, applicationName);
        this.DATA_MANAGEMENT = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_MANAGEMENT, applicationName);
        this.DATA_READ = applicationName -> authorizationService

                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_READ, applicationName);
        this.DATA_WRITE = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_WRITE, applicationName);
        this.DATA_ACCESS = applicationName -> authorizationService
                .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_ACCESS, applicationName);
    }

    @Override
    /*
    placement des permissions dans le fichier de configuration :
     */
    public boolean hasPermission(Authentication authentication, Object targetDomain, Object permission) {
        return Optional.ofNullable(authentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast)
                .map(authenticationToken -> {
                    if (SYSTEM.equals(targetDomain)) {
                        return hasPermissionForSystem(authenticationToken, permission);
                    } else if (APPLICATION.equals(targetDomain)) {
                        return hasPermissionForApplication(authenticationToken, permission);
                    }
                    return false;
                })
                .orElse(false);
    }

    private boolean hasPermissionForSystem(OreSiAuthenticationToken oreSiAuthenticationToken, Object permission) {
        return Optional.of(oreSiAuthenticationToken)
                .map(oreSiAuthenticationToken1 -> {
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

    private boolean hasPermissionForApplication(OreSiAuthenticationToken oreSiAuthenticationToken, Object permission) {
        final Optional<OreSiAuthenticationToken> oreSiAuthenticationTokenOpt = Optional.of(oreSiAuthenticationToken);
        Optional<String> applicationNameOpt = oreSiAuthenticationTokenOpt
                .map(OreSiAuthenticationToken::getApplicationName);
        if (applicationNameOpt.isEmpty()) {
            return false;
        }
        Optional<String> dataNameOpt = oreSiAuthenticationTokenOpt
                .map(OreSiAuthenticationToken::getDataName);
        return applicationNameOpt
                .flatMap(applicationName -> switch (permission) {
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
                                    .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_READ, applicationName)
                                    ::forDataRead);
                    case String read when APPLICATION_DATA_READ_SOME.equals(read) ->
                        Optional.of(authorizationService)
                            .map(as->as
                                    .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_READ, applicationName)
                                    .forDataReadSome());
                    case String read when APPLICATION_DATA_WRITE.equals(read) ->
                            dataNameOpt.flatMap(dataName -> OreSiApiRequestContext.getAuthentication()
                                    .map(OreSiAuthenticationToken::getFileOrUUID)
                                    .map(FileOrUUID::topublish)
                                    .or(() -> Optional.of(false))
                                    .map(toPublish -> authorizationService
                                            .getPrivilegeAssessorForApplication(PrivilegeApplicationDomainEnum.DATA_READ, applicationName)
                                            .forDataWrite(dataName, toPublish)));
                    case String writeFile when APPLICATION_WRITE_FILE.equals(writeFile) -> dataNameOpt
                            .map(dataName -> DATA_WRITE.apply(applicationName)
                                    .forDataWrite(dataName, false));
                    case String deleteFile when APPLICATION_DELETE_FILE.equals(deleteFile) -> dataNameOpt
                            .map(dataName -> DATA_READ.apply(applicationName)
                                    .forDataDelete(dataName));
                    case String downloadBundle when APPLICATION_DATA_DOWNLOAD_BUNDLE.equals(downloadBundle) ->
                            Optional.of(APPLICATION_MANAGER.apply(applicationName)
                                    .forDownloadBundle());
                    default -> Optional.empty();
                })
                .map(applicationPersona -> {
                    oreSiAuthenticationTokenOpt
                            .ifPresent(oreSiAuthenticationToken1 -> oreSiAuthenticationToken.setApplicationPersonna(applicationPersona));
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