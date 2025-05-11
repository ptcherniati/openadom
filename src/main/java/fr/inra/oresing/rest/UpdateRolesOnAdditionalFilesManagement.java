package fr.inra.oresing.rest;

import com.google.common.collect.Sets;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.*;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateRolesOnAdditionalFilesManagement {
    private Set<UUID> previousUsers;
    private Set<UUID> newUsers;
    private OreSiAdditionalFileAuthorization modifiedAuthorization;
    private final OreSiRepository repository;
    final SqlService db;
    final AuthenticationService authenticationService;
    private Application application;
    private AuthorizationAdditionalFilesRepository authorizationAdditionalFilesRepository;

    public UpdateRolesOnAdditionalFilesManagement(final OreSiRepository repository, final SqlService db, final AuthenticationService authenticationService) {
        super();
        this.repository = repository;
        this.db = db;
        this.authenticationService = authenticationService;
    }

    public void init(final Set<UUID> previousUsers, final OreSiAdditionalFileAuthorization modifiedAuthorization) {
        this.previousUsers = previousUsers;
        newUsers = modifiedAuthorization.getOreSiUsers();
        this.modifiedAuthorization = modifiedAuthorization;
        application = repository.application().findApplication(modifiedAuthorization.getApplication());
        authorizationAdditionalFilesRepository = repository.getRepository(application).authorizationAdditionalFiles();

    }

    public void updateRoleForManagement() {
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        addOrRemoveAuthorizationForUsers(previousUsers, newUsers, oreSiRightOnApplicationRole);
        dropPolicies(oreSiRightOnApplicationRole);
        if (modifiedAuthorization.getAdditionalFiles().containsKey(OperationAdditionalFileType.admin)) {
            toAdditionalFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.admin, List.of(SqlPolicy.Statement.ALL))
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.admin, List.of(SqlPolicy.Statement.ALL))
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getAdditionalFiles().containsKey(OperationAdditionalFileType.delete)) {
            toAdditionalFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.delete, List.of(SqlPolicy.Statement.DELETE))
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.admin, List.of(SqlPolicy.Statement.ALL))
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getAdditionalFiles().containsKey(OperationAdditionalFileType.depot)) {
            toAdditionalFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.depot, List.of(SqlPolicy.Statement.INSERT))
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationAdditionalFileType.admin, List.of(SqlPolicy.Statement.ALL))
                    .forEach(db::createPolicy);
        }
    }

    public void dropPolicies(final OreSiRightOnApplicationRole oreSiRightOnApplicationRole) {
        db.getPoliciesForRole(oreSiRightOnApplicationRole).stream()
                .map(policyDescription -> new SqlPolicy(
                        policyDescription.getPolicyname(),
                        SqlSchema.forApplication(application).forTableName(policyDescription.getTablename()),
                        null,
                        null,
                        oreSiRightOnApplicationRole,
                        null,
                        null)
                )
                .forEach(db::dropPolicy);
    }


    private void addOrRemoveAuthorizationForUsers(final Set<UUID> previousUsers, final Set<UUID> newUsers, final OreSiRightOnApplicationRole oreSiRightOnApplicationRole) {
        final Set<UUID> usersNotChanged = Sets.difference(previousUsers, newUsers);
        previousUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.removeUserInRole(user, oreSiRightOnApplicationRole));
        newUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.addUserInRole(user, oreSiRightOnApplicationRole));
    }


    private List<SqlPolicy> toAdditionalFilePolicy(final OreSiAdditionalFileAuthorization authorization, final OreSiRightOnApplicationRole oreSiRightOnApplicationRole, final OperationAdditionalFileType operation, final List<SqlPolicy.Statement> statements) {
        final Set<String> usingExpressionElements = new LinkedHashSet<>();
        final SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        final SqlPolicy sqlPolicy = null;
        final String expression = createExpression(authorization, usingExpressionElements, application, sqlSchemaForApplication, operation);
        String usingExpression = null, checkExpression = null;

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAdditionalFileAuthorization.class.getSimpleName() + "_" + authorization.getId().toString().substring(0, 13) + "_AdditionalFile_" + statement.name().substring(0, 3),
                        sqlSchemaForApplication.additionalBinaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.UPDATE || statement == SqlPolicy.Statement.SELECT || statement == SqlPolicy.Statement.DELETE ? expression : null,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.INSERT || statement == SqlPolicy.Statement.UPDATE ? expression : null
                ))
                .collect(Collectors.toList());
    }


    private List<SqlPolicy> toBinaryFilePolicy(final OreSiAdditionalFileAuthorization authorization, final OreSiRightOnApplicationRole oreSiRightOnApplicationRole, final OperationAdditionalFileType operation, final List<SqlPolicy.Statement> statements) {
        final Set<String> usingExpressionElements = new LinkedHashSet<>();
        final SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        final SqlPolicy sqlPolicy = null;

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString().substring(0, 13)+ "_bf_" + "_AdditionalFile_" +  statement.name().substring(0, 3),
                        sqlSchemaForApplication.binaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.UPDATE || statement == SqlPolicy.Statement.SELECT || statement == SqlPolicy.Statement.DELETE ? "true" : null,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.INSERT || statement == SqlPolicy.Statement.UPDATE ? "true" : null
                ))
                .collect(Collectors.toList());
    }

    private static String createExpression(final OreSiAdditionalFileAuthorization authorization, final Set<String> usingExpressionElements, final Application application, final SqlSchemaForApplication sqlSchemaForApplication, final OperationAdditionalFileType operation) {
        if (authorization.getAdditionalFiles().containsKey(operation) &&
                !CollectionUtils.isEmpty(authorization.getAdditionalFiles().get(operation))) {
                   return authorization.getAdditionalFiles().get(operation).stream()
                            .collect(Collectors.joining(",", "filetype  = any('{" ,"}'::text[])")


            );
        }
        return "";
    }

    public UUID revoke(final Application application, final UUID authorizationId) {
        this.application = application;
        authorizationAdditionalFilesRepository = repository.getRepository(application).authorizationAdditionalFiles();
        final OreSiAdditionalFileAuthorization oreSiAuthorization = authorizationAdditionalFilesRepository.findById(authorizationId);
        dropPolicies(OreSiRightOnApplicationRole.managementRole(application, authorizationId));
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, authorizationId);
        authenticationService.setRoleAdmin();
        oreSiAuthorization.getOreSiUsers().stream()
                .map(authenticationService::getUserRole)
                .forEach(user -> db.removeUserInRole(user, oreSiRightOnApplicationRole));
        authenticationService.setRoleForClient();
        authorizationAdditionalFilesRepository.delete(authorizationId);
        authenticationService.setRoleAdmin();
        db.dropRole(oreSiRightOnApplicationRole);
        authenticationService.setRoleForClient();
        return authorizationId;
    }
}