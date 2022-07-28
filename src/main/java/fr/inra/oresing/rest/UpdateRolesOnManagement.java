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
        this.hasRepository = Optional.of(application.getConfiguration())
                .map(Configuration::getDataTypes)
                .map(map -> map.get(modifiedAuthorization.getDataType()))
                .map(Configuration.DataTypeDescription::getRepository)
                .isPresent();
        this.authorizationRepository = repository.getRepository(application).authorization();

    }

    public void updateRoleForManagement() {
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.readerOn(application));
        addOrRemoveAuthorizationForUsers(previousUsers, newUsers, oreSiRightOnApplicationRole);

        final String expression = String.format("name = '%s'", application.getName());
        final SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", "application", "reader", oreSiRightOnApplicationRole.getAsSqlRole()),
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                List.of(SqlPolicy.Statement.SELECT),
                oreSiRightOnApplicationRole,
                expression,
                null
        );
        db.createPolicy(sqlPolicy);
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.publication)) {
            db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.writerOn(application));
            toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                    .forEach(db::createPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.delete)) {
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.delete, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.DELETE)).stream()
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.depot)) {
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.depot, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                    .forEach(db::createPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.extraction)) {
            toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.extraction, List.of(SqlPolicy.Statement.SELECT)).stream()
                    .forEach(db::createPolicy);
        }
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


    private List<SqlPolicy> toDatatypePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, OperationType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);

        String dataType = authorization.getDataType();
        SqlPolicy sqlPolicy = null;
        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        usingExpressionElements.add("dataType = '" + dataType + "'");
        String expression = createExpression(authorization, usingExpressionElements, application, sqlSchemaForApplication, operation);
        String usingExpression = null, checkExpression = null;

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString() + "_data_"+statement.name().substring(0,3),
                        sqlSchemaForApplication.data(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) ? expression : null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE) ? expression : null
                ))
                .collect(Collectors.toList());
    }


    private List<SqlPolicy> toBinaryFilePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, OperationType operation, List<SqlPolicy.Statement> statements) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        SqlPolicy sqlPolicy = null;
        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        String expression = hasRepository ? createBinaryExpression(authorization, usingExpressionElements, application, sqlSchemaForApplication, operation) : "true";

        return statements.stream()
                .map(statement -> new SqlPolicy(
                        OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString()+ "_bf_"+statement.name().substring(0,3),
                        sqlSchemaForApplication.binaryFile(),
                        SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                        Collections.singletonList(statement),
                        oreSiRightOnApplicationRole,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.UPDATE) || statement.equals(SqlPolicy.Statement.SELECT) ? /*expression*/ "true": null,
                        statement.equals(SqlPolicy.Statement.ALL) || statement.equals(SqlPolicy.Statement.INSERT) || statement.equals(SqlPolicy.Statement.UPDATE) ? expression : null
                ))
                .collect(Collectors.toList());
    }

    private String createBinaryExpression(OreSiAuthorization authorization, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationType operation) {
        if (authorization.getAuthorizations().containsKey(operation) &&
                !authorization.getAuthorizations().get(operation).isEmpty()) {
            usingExpressionElements.add("\"authorization\" @> " +
                    authorization.getAuthorizations().get(operation).stream()
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

    private String createExpression(OreSiAuthorization authorization, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationType operation) {
        if (authorization.getAuthorizations().containsKey(operation) &&
                !authorization.getAuthorizations().get(operation).isEmpty()) {
            usingExpressionElements.add("\"authorization\" @> " +
                    authorization.getAuthorizations().get(operation).stream()
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

    public void revoke(AuthorizationRequest revokeAuthorizationRequest) {
        this.application = repository.application().findApplication(revokeAuthorizationRequest.getApplicationNameOrId());
        this.authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = revokeAuthorizationRequest.getAuthorizationId();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, authorizationId);
        final SqlPolicy sqlPolicy = new SqlPolicy(
                String.join("_", "application", "reader", oreSiRightOnApplicationRole.getAsSqlRole()),
                SqlSchema.main().application(),
                null,
                null,
                null,
                null,
                null
        );
        modifiedAuthorization = authorizationRepository.findById(revokeAuthorizationRequest.getAuthorizationId());
        db.dropPolicy(sqlPolicy);
        if (oreSiAuthorization.getAuthorizations().containsKey(OperationType.publication)) {
            db.removeUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.writerOn(application));
            toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT)).stream()
                    .forEach(db::dropPolicy);
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.publication, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE)).stream()
                    .forEach(db::dropPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.delete)) {
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.delete, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.DELETE)).stream()
                    .forEach(db::dropPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.depot)) {
            toBinaryFilePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.depot, List.of(SqlPolicy.Statement.SELECT, SqlPolicy.Statement.INSERT, SqlPolicy.Statement.UPDATE, SqlPolicy.Statement.INSERT)).stream()
                    .forEach(db::dropPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.extraction)) {
            toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.extraction, List.of(SqlPolicy.Statement.SELECT)).stream()
                    .forEach(db::dropPolicy);
        }
        authorizationRepository.delete(authorizationId);
    }
}