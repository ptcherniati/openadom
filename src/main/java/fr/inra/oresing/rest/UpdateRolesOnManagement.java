package fr.inra.oresing.rest;

import com.google.common.collect.Sets;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.index.AuthorizationIndex;

import java.util.*;

public class UpdateRolesOnManagement {
    final SqlService db;
    final AuthenticationService authenticationService;
    private final OreSiRepository repository;
    private Set<UUID> previousUsers;
    private OreSiAuthorization modifiedAuthorization;
    private Application application;
    private AuthorizationRepository authorizationRepository;

    public UpdateRolesOnManagement(final OreSiRepository repository, final SqlService db, final AuthenticationService authenticationService) {
        super();
        this.repository = repository;
        this.db = db;
        this.authenticationService = authenticationService;
    }

    public void init(final Set<UUID> previousUsers, final OreSiAuthorization modifiedAuthorization) {
        this.previousUsers = previousUsers;
        //Set<UUID> newUsers = modifiedAuthorization.getOreSiUsers();
        this.modifiedAuthorization = modifiedAuthorization;
        /*application = repository.application().findApplication(modifiedAuthorization.getApplication());
        boolean hasRepository = modifiedAuthorization.getAuthorizations().keySet()
                .stream().anyMatch(dataName -> application.findSubmission(dataName)
                        .map(Submission::strategy)
                        .map(SubmissionType.OA_VERSIONING::equals)
                        .isPresent());
        authorizationRepository = repository.getRepository(application).authorization();*/

    }

    public void updateRoleForManagement() {
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        addOrRemoveAuthorizationForUsers(previousUsers, new HashSet<>(modifiedAuthorization.getOreSiUsers()), oreSiRightOnApplicationRole);
        dropPolicies(oreSiRightOnApplicationRole);

        for (Map.Entry<String, AuthorizationForScope> entry : modifiedAuthorization.getAuthorizations().entrySet()) {
            String dataName = entry.getKey();
            AuthorizationForScope authorization = entry.getValue();

            Set<OperationType> operationTypes = authorization.operationTypes();
            createDataPolicies(modifiedAuthorization, oreSiRightOnApplicationRole, dataName, operationTypes);
            if (operationTypes.contains(OperationType.publication) ||
                    operationTypes.contains(OperationType.delete) ||
                    operationTypes.contains(OperationType.depot)) {
                createBinaryFilePolicies(modifiedAuthorization, oreSiRightOnApplicationRole, dataName, operationTypes);
            }
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

    private void createDataPolicies(OreSiAuthorization authorization, OreSiRightOnApplicationRole role,
                                    String dataName, Set<OperationType> operationTypes) {
        AuthorizationIndex authorizationIndex = new AuthorizationIndex(application);
        AuthorizationForScope authForScope = authorization.getAuthorizations().get(dataName);

        String baseExpression = authorizationIndex.sqlFilterForAuthorization(dataName, authForScope, false);
        String timeScopeExpression = authorizationIndex.sqlFilterForAuthorization(dataName, authForScope, true);

        boolean hasTimeScope = authForScope.timeScope() != null;

        Set<SqlPolicy.Statement> statementsWithTimeScope = new HashSet<>();
        Set<SqlPolicy.Statement> statementsWithoutTimeScope = new HashSet<>();

        if (operationTypes.contains(OperationType.publication)) {
            statementsWithoutTimeScope.addAll(List.of(SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE, SqlPolicy.Statement.DELETE, SqlPolicy.Statement.SELECT));
        }
        if (operationTypes.contains(OperationType.delete)) {
            statementsWithoutTimeScope.add(SqlPolicy.Statement.DELETE);
        }
        if (operationTypes.contains(OperationType.extraction)) {
            if (hasTimeScope) {
                statementsWithTimeScope.add(SqlPolicy.Statement.SELECT);
            } else {
                statementsWithoutTimeScope.add(SqlPolicy.Statement.SELECT);
            }
        }

        if (!statementsWithTimeScope.isEmpty()) {
            toDataPolicy(authorization, role, dataName, timeScopeExpression, statementsWithTimeScope)
                    .forEach(db::createPolicy);
        }
        if (!statementsWithoutTimeScope.isEmpty()) {
            toDataPolicy(authorization, role, dataName, timeScopeExpression, statementsWithoutTimeScope)
                    .forEach(db::createPolicy);
        }
    }

    private void createBinaryFilePolicies(OreSiAuthorization authorization, OreSiRightOnApplicationRole role,
                                          String dataName, Set<OperationType> operationTypes) {
        AuthorizationIndex authorizationIndex = new AuthorizationIndex(application);
        AuthorizationForScope authForScope = authorization.getAuthorizations().get(dataName);

        String expression = authorizationIndex.sqlFilterForAuthorization(dataName, authForScope, false);

        Set<SqlPolicy.Statement> statements = new HashSet<>();
        if (operationTypes.contains(OperationType.publication) || operationTypes.contains(OperationType.depot)) {
            statements.addAll(List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE));
        }
        if (operationTypes.contains(OperationType.delete)) {
            statements.addAll(List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.DELETE));
        }

        if (!statements.isEmpty()) {
            toBinaryFilePolicy(authorization, role, dataName, statements)
                    .forEach(db::createPolicy);
        }
    }

    private List<SqlPolicy> toDataPolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole role,
                                         String dataName, String expression, Set<SqlPolicy.Statement> statements) {
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        return statements.stream()
                .map(statement -> new SqlPolicy(
                        authorization.toIdForReference(statement, dataName),
                        sqlSchemaForApplication.referenceValue(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        role,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.UPDATE ||
                                statement == SqlPolicy.Statement.SELECT || statement == SqlPolicy.Statement.DELETE ? expression : null,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.INSERT ||
                                statement == SqlPolicy.Statement.UPDATE ? expression : null
                ))
                .toList();
    }


    private List<SqlPolicy> toBinaryFilePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole role,
                                               String dataName, Set<SqlPolicy.Statement> statements) {
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString() + "_bf_" + statement.name().substring(0, 3),
                        sqlSchemaForApplication.binaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        role,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.UPDATE ||
                                statement == SqlPolicy.Statement.SELECT || statement == SqlPolicy.Statement.DELETE ? "true" : null,
                        statement == SqlPolicy.Statement.ALL || statement == SqlPolicy.Statement.INSERT ||
                                statement == SqlPolicy.Statement.UPDATE ? "true" : null
                ))
                .toList();
    }

    public UUID revoke(final AuthorizationRequest revokeAuthorizationRequest) {
        application = repository.application().findApplication(revokeAuthorizationRequest.applicationId());
        authorizationRepository = repository.getRepository(application).authorization();
        final UUID authorizationId = revokeAuthorizationRequest.authorizationId();
        final OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        authenticationService.setRoleAdmin();
        dropPolicies(OreSiRightOnApplicationRole.managementRole(application, revokeAuthorizationRequest.authorizationId()));
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, authorizationId);
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