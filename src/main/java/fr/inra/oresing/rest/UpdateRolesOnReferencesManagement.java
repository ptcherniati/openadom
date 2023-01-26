package fr.inra.oresing.rest;

import com.google.common.collect.Sets;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiReferenceAuthorization;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateRolesOnReferencesManagement {
    private Set<UUID> previousUsers;
    private Set<UUID> newUsers;
    private OreSiReferenceAuthorization modifiedAuthorization;
    private OreSiRepository repository;
    SqlService db;
    AuthenticationService authenticationService;
    private Application application;
    private AuthorizationRepository authorizationRepository;

    public UpdateRolesOnReferencesManagement(OreSiRepository repository, SqlService db, AuthenticationService authenticationService) {
        this.repository = repository;
        this.db = db;
        this.authenticationService = authenticationService;
    }

    public void init(Set<UUID> previousUsers, OreSiReferenceAuthorization modifiedAuthorization) {
        this.previousUsers = previousUsers;
        this.newUsers = modifiedAuthorization.getOreSiUsers();
        this.modifiedAuthorization = modifiedAuthorization;
        this.application = repository.application().findApplication(modifiedAuthorization.getApplication());
        this.authorizationRepository = repository.getRepository(application).authorization();

    }

    public void updateRoleForManagement() {
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        addOrRemoveAuthorizationForUsers(previousUsers, newUsers, oreSiRightOnApplicationRole);
        dropPolicies(oreSiRightOnApplicationRole);
        if (modifiedAuthorization.getReferences().containsKey(OperationReferenceType.admin)) {
            toReferencePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationReferenceType.admin, List.of(SqlPolicy.Statement.ALL)).stream()
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationReferenceType.admin, List.of(SqlPolicy.Statement.ALL)).stream()
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getReferences().containsKey(OperationReferenceType.manage)) {
            toReferencePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationReferenceType.manage, List.of(SqlPolicy.Statement.ALL)).stream()
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationReferenceType.admin, List.of(SqlPolicy.Statement.ALL)).stream()
                    .forEach(db::createPolicy);
        }
    }

    public void dropPolicies(OreSiRightOnApplicationRole oreSiRightOnApplicationRole) {
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


    private void addOrRemoveAuthorizationForUsers(Set<UUID> previousUsers, Set<UUID> newUsers, OreSiRightOnApplicationRole oreSiRightOnApplicationRole) {
        Set<UUID> usersNotChanged = Sets.difference(previousUsers, newUsers);
        previousUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.removeUserInRole(user, oreSiRightOnApplicationRole));
        newUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.addUserInRole(user, oreSiRightOnApplicationRole));
    }


    private List<SqlPolicy> toReferencePolicy(OreSiReferenceAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, OperationReferenceType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        SqlPolicy sqlPolicy = null;
        String expression = createExpression(authorization, usingExpressionElements, application, sqlSchemaForApplication, operation);
        String usingExpression = null, checkExpression = null;

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiReferenceAuthorization.class.getSimpleName() + "_" + authorization.getId().toString().substring(0, 13) + "_reference_" + statement.name().substring(0, 3),
                        sqlSchemaForApplication.referenceValue(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) || statement.equals(SqlPolicy.Statement.DELETE) ? expression : null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE)  ? expression : null
                ))
                .collect(Collectors.toList());
    }


    private List<SqlPolicy> toBinaryFilePolicy(OreSiReferenceAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, OperationReferenceType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        SqlPolicy sqlPolicy = null;

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString().substring(0, 13)+ "_bf_" + "_reference_" +  statement.name().substring(0, 3),
                        sqlSchemaForApplication.binaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) ? "true" : null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE) ? "true" : null
                ))
                .collect(Collectors.toList());
    }

    private String createExpression(OreSiReferenceAuthorization authorization, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationReferenceType operation) {
        if (authorization.getReferences().containsKey(operation) &&
                !CollectionUtils.isEmpty(authorization.getReferences().get(operation))) {
                   return authorization.getReferences().get(operation).stream()
                            .collect(Collectors.joining(",", "referenceType  = any('{" ,"}'::text[])")


            );
        }
        return "";
    }

    public UUID revoke(AuthorizationRequest revokeAuthorizationRequest) {
        this.application = repository.application().findApplication(revokeAuthorizationRequest.getApplicationNameOrId());
        this.authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = revokeAuthorizationRequest.getAuthorizationId();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        dropPolicies(OreSiRightOnApplicationRole.managementRole(application, revokeAuthorizationRequest.getAuthorizationId()));
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, authorizationId);
        authenticationService.setRoleAdmin();
        oreSiAuthorization.getOreSiUsers().stream()
                .map(authenticationService::getUserRole)
                .forEach(user -> db.removeUserInRole(user, oreSiRightOnApplicationRole));
        authenticationService.setRoleForClient();
        authorizationRepository.delete(authorizationId);
        authenticationService.setRoleAdmin();
        db.dropRole(oreSiRightOnApplicationRole);
        authenticationService.setRoleForClient();
        return authorizationId;
    }
}