package fr.inra.oresing.persistence.flyway;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiApplicationCreatorRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.*;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SchemaFlywayCallback implements Callback {
    private final Application application;
    private final OreSiUserRole creator;
    private final Map<String, MigrateService.ActionToDoAfterMigration> callBackFunction;
    private final SqlSchemaForApplication sqlSchemaForApplication;
    private final OreSiRightOnApplicationRole writerOnApplicationRole;
    private final OreSiApplicationCreatorRole applicationCreator;
    private final OreSiRightOnApplicationRole applicationManagerOnApplicationRole;
    private final OreSiRightOnApplicationRole userManagerOnApplicationRole;
    private final OreSiRightOnApplicationRole readerOnApplicationRole;
    private final String currentDatabase;

    public SchemaFlywayCallback(
            Application application,
            String currentDatabase,
            AuthenticationService authenticationService,
            OreSiUserRole creator,
            Map<String, MigrateService.ActionToDoAfterMigration> callBackFunction) {
        this.application = application;
        this.currentDatabase = currentDatabase;
        this.creator = creator;
        this.callBackFunction = callBackFunction;
        this.applicationCreator = OreSiRole.applicationCreator();
        this.sqlSchemaForApplication = SqlSchema.forApplication(application);
        this.applicationManagerOnApplicationRole = OreSiRightOnApplicationRole.adminOn(application);
        this.userManagerOnApplicationRole = OreSiRightOnApplicationRole.userAdminOn(application);
        this.readerOnApplicationRole = OreSiRightOnApplicationRole.readerOn(application);
        this.writerOnApplicationRole = OreSiRightOnApplicationRole.writerOn(application);

    }

    @Override
    public boolean supports(Event event, Context context) {
        return (creator!= null && event == Event.BEFORE_MIGRATE) || event == Event.AFTER_EACH_MIGRATE || event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE || event == Event.AFTER_EACH_MIGRATE;
    }
    public void handle(Event event, Context context) {
        if (event.equals(Event.BEFORE_MIGRATE)) {
            beforeMigrate(context);
        }
        if (event.equals(Event.AFTER_MIGRATE)) {
            changeOwner(context);
        }
        if (event.equals(Event.AFTER_EACH_MIGRATE)) {
            afterEachMigrate(context);
        }

    }

    private void changeOwner(Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            // Changer le propriétaire du schéma
            statement.execute("ALTER SCHEMA %s OWNER TO \"%s\""
                    .formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));

            // Changer le propriétaire des tables
            ResultSet rs = statement.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = '%s'"
                            .formatted(sqlSchemaForApplication.getName())
            );
            List<String> tableNames = new ArrayList<>();
            while (rs.next()) {
                String tableName = rs.getString("tablename");
                if(tableName.equals("flyway_schema_history")) {
                    continue;
                }
                tableNames.add(tableName);
            }
            for (String tableName : tableNames) {
                statement.execute("ALTER TABLE %s.%s OWNER TO \"%s\""
                        .formatted(sqlSchemaForApplication.getName(), tableName, userManagerOnApplicationRole.getAsSqlRole()));

            }

            // Accorder les privilèges nécessaires
            statement.execute("GRANT USAGE ON SCHEMA %s TO \"%s\""
                    .formatted(sqlSchemaForApplication.getName(), applicationCreator.getAsSqlRole()));

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    private void afterEachMigrate(Context context) {
        final Connection connection = context.getConnection();
        String version = context.getMigrationInfo().getVersion().getVersion();
        Optional.ofNullable(callBackFunction.get(version))
                .ifPresent(actionToDoAfterMigration -> {
                    try {
                        actionToDoAfterMigration.execute(connection);
                    } catch (final SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void beforeMigrate(Context context) {
        try (Statement statement = context.getConnection().createStatement()) {
            configureRoles(statement, applicationManagerOnApplicationRole, userManagerOnApplicationRole, readerOnApplicationRole, writerOnApplicationRole, sqlSchemaForApplication, applicationCreator);
            createSchema(statement, sqlSchemaForApplication, applicationManagerOnApplicationRole);
            setPrivilegesForApplicationManagerToExecuteUpdate(statement, sqlSchemaForApplication, userManagerOnApplicationRole);
            setPrivilegesForUserManagerToAccesSchema(statement, sqlSchemaForApplication, userManagerOnApplicationRole);
            setRoleUserManager(statement, applicationManagerOnApplicationRole);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setRoleUserManager(Statement statement, OreSiRightOnApplicationRole userManagerOnApplicationRole) throws SQLException {
        statement.execute("Set role \"%1$s\"".formatted(userManagerOnApplicationRole.getAsSqlRole()));
    }

    private void setPrivilegesForApplicationManagerToExecuteUpdate(Statement statement, SqlSchemaForApplication sqlSchemaForApplication, OreSiRightOnApplicationRole applicationManagerOnApplicationRole) throws SQLException {
        // Accès complet au schéma de l'application
        statement.execute("GRANT ALL ON SCHEMA %s TO \"%s\"".formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));

        // Privilèges par défaut pour les nouveaux objets dans le schéma
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT ALL ON TABLES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT ALL ON SEQUENCES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT ALL ON FUNCTIONS TO \"%s\"".formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT ALL ON TYPES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), applicationManagerOnApplicationRole.getAsSqlRole()));

        // Accès aux tables publiques
        statement.execute("GRANT USAGE ON SCHEMA public TO \"%s\"".formatted(applicationManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"%s\"".formatted(applicationManagerOnApplicationRole.getAsSqlRole()));
        
        /*
            applicationManager all acces to application for applicationName
         */
        statement.execute(new SqlPolicy(
                String.join("_", applicationManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                List.of(SqlPolicy.Statement.ALL),
                applicationManagerOnApplicationRole,
                "name = '" + application.getName() + "'",
                null
        ).policyToCreateSql());
        /*
                for reader of application reader rights on application on public
             */
        statement.execute(new SqlPolicy(
                String.join("_", readerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
                OreSiSqlSchema.application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                List.of(SqlPolicy.Statement.SELECT),
                readerOnApplicationRole,
                "name = '" + application.getName() + "'",
                null
        ).policyToCreateSql());
    }

    private void setPrivilegesForUserManagerToAccesSchema(Statement statement, SqlSchemaForApplication sqlSchemaForApplication, OreSiRightOnApplicationRole userManagerOnApplicationRole) throws SQLException {
        // Accès en lecture et écriture sur le schéma de l'application
        statement.execute("GRANT USAGE, CREATE ON SCHEMA %s TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));

        // Privilèges par défaut pour les nouveaux objets dans le schéma
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT EXECUTE ON FUNCTIONS TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA %s GRANT USAGE ON TYPES TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));

        // Accorder les privilèges sur les objets existants
        statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %s TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA %s TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %s TO \"%s\"".formatted(sqlSchemaForApplication.getName(), userManagerOnApplicationRole.getAsSqlRole()));
    }

    private static void createSchema(Statement statement, SqlSchemaForApplication sqlSchemaForApplication, OreSiRightOnApplicationRole applicationManagerOnApplicationRole) throws SQLException {
        statement.execute(
                "CREATE SCHEMA IF NOT EXISTS %s AUTHORIZATION \"%s\""
                        .formatted(
                                sqlSchemaForApplication.getName(),
                                applicationManagerOnApplicationRole.getAsSqlRole()
                        )
        );
    }

    private void configureRoles(Statement statement, OreSiRightOnApplicationRole applicationManagerOnApplicationRole, OreSiRightOnApplicationRole userManagerOnApplicationRole, OreSiRightOnApplicationRole readerOnApplicationRole, OreSiRightOnApplicationRole writerOnApplicationRole, SqlSchemaForApplication sqlSchemaForApplication, OreSiApplicationCreatorRole applicationCreator) throws SQLException {
        statement.execute(applicationManagerOnApplicationRole.toSqlCreaterole("Application manager of application %s".formatted(application.getName())));
        statement.execute(userManagerOnApplicationRole.toSqlCreaterole("User manager of application %s".formatted(application.getName())));
        statement.execute(readerOnApplicationRole.toSqlCreaterole("Reader role on application %s\n Must have good policies".formatted(application.getName())));
        statement.execute(writerOnApplicationRole.toSqlCreaterole("Writer role on application %s\n Must have good policies".formatted(application.getName())));

        statement.execute(
                "GRANT CREATE ON DATABASE \"%1$s\" TO \"%2$s\""
                        .formatted(currentDatabase, applicationManagerOnApplicationRole.getAsSqlRole()));

        statement.execute(readerOnApplicationRole.addUserInRoleSql(writerOnApplicationRole, true));
        statement.execute(writerOnApplicationRole.addUserInRoleSql(userManagerOnApplicationRole, true));
        statement.execute(userManagerOnApplicationRole.addUserInRoleSql(applicationManagerOnApplicationRole, false));
        statement.execute(applicationCreator.addUserInRoleSql(applicationManagerOnApplicationRole, false));
        statement.execute(applicationManagerOnApplicationRole.addUserInRoleSql(creator, false));
        statement.execute("GRANT \"%1$s\" to current_user WITH INHERIT TRUE".formatted(applicationManagerOnApplicationRole.getAsSqlRole()));
        statement.execute("GRANT \"%1$s\" to \"%2$s\" WITH INHERIT TRUE".formatted(applicationManagerOnApplicationRole.getAsSqlRole(), creator.getAsSqlRole()));
    }

    @Override
    public String getCallbackName() {
        return "";
    }
}
