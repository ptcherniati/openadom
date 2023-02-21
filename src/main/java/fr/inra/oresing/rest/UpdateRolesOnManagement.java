package fr.inra.oresing.rest;

import com.google.common.collect.Sets;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateRolesOnManagement {
    private Set<UUID> previousUsers;
    private Set<UUID> newUsers;
    private OreSiAuthorization modifiedAuthorization;
    private boolean hasRepository;
    private OreSiRepository repository;
    SqlService db;
    AuthenticationService authenticationService;
    private Application application;
    private AuthorizationRepository authorizationRepository;

    public UpdateRolesOnManagement(OreSiRepository repository, SqlService db, AuthenticationService authenticationService) {
        this.repository = repository;
        this.db = db;
        this.authenticationService = authenticationService;
    }

    public void init(Set<UUID> previousUsers, OreSiAuthorization modifiedAuthorization) {
        this.previousUsers = previousUsers;
        this.newUsers = modifiedAuthorization.getOreSiUsers();
        this.modifiedAuthorization = modifiedAuthorization;
        this.application = repository.application().findApplication(modifiedAuthorization.getApplication());
        this.hasRepository =
                modifiedAuthorization.getAuthorizations().keySet()
                        .stream().anyMatch(datatype -> Optional.of(application.getConfiguration())
                                .map(Configuration::getDataTypes)
                                .map(map -> map.get(datatype))
                                .map(Configuration.DataTypeDescription::getRepository)
                                .isPresent());
        this.authorizationRepository = repository.getRepository(application).authorization();

    }

    public void updateRoleForManagement() {
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        addOrRemoveAuthorizationForUsers(previousUsers, newUsers, oreSiRightOnApplicationRole);
        dropPolicies(oreSiRightOnApplicationRole);
        for (String datatype : modifiedAuthorization.getAuthorizations().keySet()) {
            if (modifiedAuthorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).containsKey(OperationType.publication)) {
                toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                        .forEach(db::createPolicy);
                toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                        .forEach(db::createPolicy);
            }
            if (modifiedAuthorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).containsKey(OperationType.delete)) {
                toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.delete, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.SELECT, SqlPolicy.Statement.DELETE)).stream()
                        .forEach(db::createPolicy);
                toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.delete, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.DELETE)).stream()
                        .forEach(db::createPolicy);
            }
            if (modifiedAuthorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).containsKey(OperationType.depot)) {
                toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.depot, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                        .forEach(db::createPolicy);
            }
            if (modifiedAuthorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).containsKey(OperationType.extraction)) {
                toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, datatype, OperationType.extraction, List.of(SqlPolicy.Statement.SELECT)).stream()
                        .forEach(db::createPolicy);
            }
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


    private List<SqlPolicy> toDatatypePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, String datatype, OperationType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        SqlPolicy sqlPolicy = null;
        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        String expression = createExpressionForDatatypePolicy(authorization, datatype, usingExpressionElements, application, sqlSchemaForApplication, operation);
        String usingExpression = null, checkExpression = null;


        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString() + "_data_" + statement.name().substring(0, 3),
                        sqlSchemaForApplication.data(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) || statement.equals(SqlPolicy.Statement.DELETE) ? expression : null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE) ? expression : null
                ))
                .collect(Collectors.toList());
    }


    private List<SqlPolicy> toBinaryFilePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, String datatype, OperationType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        SqlPolicy sqlPolicy = null;
        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        String expression = hasRepository ? createExpressionForBinaryFilePolicy(authorization, datatype, usingExpressionElements, application, sqlSchemaForApplication, operation) : "true";

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString() + "_bf_" + statement.name().substring(0, 3),
                        sqlSchemaForApplication.binaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) ? /*expression*/ "true" : null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE) ? expression : null
                ))
                .collect(Collectors.toList());
    }

    private String createExpressionForBinaryFilePolicy(OreSiAuthorization authorization, String datatype, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationType operation) {
        if (authorization.getAuthorizations().values().stream().anyMatch(v -> v.containsKey(operation) &&
                !v.get(operation).isEmpty())) {
            usingExpressionElements.add("\"datatype\"='" + datatype + "' and " +
                    "\"authorization\" @> " +
                    authorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).get(operation).stream()
                            .map(auth -> auth.toSQL(application.getConfiguration().getRequiredAuthorizationsAttributes()))
                            .filter(auth -> auth != null)
                            .map(sql -> String.format(sql, sqlSchemaForApplication.getName()))
                            .collect(Collectors.joining(",", "ARRAY[", "]::" + sqlSchemaForApplication.getName() + ".authorization[]"))


            );
        }

        String usingExpression = usingExpressionElements.stream()
                .map(statement -> "(" + statement + ")")
                .collect(Collectors.joining(" AND "));
        return usingExpression;
    }

    private String createExpressionForDatatypePolicy(OreSiAuthorization authorization, String datatype, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationType operation) {
        if (authorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).containsKey(operation) &&
                !authorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).get(operation).isEmpty()) {
            usingExpressionElements.add("\"datatype\"='" + datatype + "' and " +
                    "\"authorization\" @> " +
                    authorization.getAuthorizations().getOrDefault(datatype, new HashMap<>()).get(operation).stream()
                            .map(auth -> auth.toSQL(application.getConfiguration().getRequiredAuthorizationsAttributes()))
                            .filter(auth -> auth != null)
                            .map(sql -> String.format(sql, sqlSchemaForApplication.getName()))
                            .collect(Collectors.joining(",", "ARRAY[", "]::" + sqlSchemaForApplication.getName() + ".authorization[]"))


            );
        }

        String usingExpression = usingExpressionElements.stream()
                .map(statement -> "(" + statement + ")")
                .collect(Collectors.joining(" AND "));
        return usingExpression;
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