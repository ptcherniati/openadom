package fr.inra.oresing.persistence.flyway;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.OreSiService;
import lombok.extern.java.Log;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

@Component
@Log
public class MigrateService {
    @Autowired
    ApplicationRepository applicationRepository;
    @Autowired
    OreSiService oreSiService;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private SqlService db;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DataSource dataSource;

    public void setApplication(Application application) {
        this.application = application;
    }

    private Application application;
    Map<String, ActionToDoAfterMigration> callBackFunction =
            new LinkedHashMap<>() {{
                put("1", new Migrate1());
                put("2", new Migrate2());
                put("3", new Migrate3());
            }};

    @Autowired
    private BeanFactory beanFactory;

    public void migrateAll() {
        log.info("\n**************************************\n" +
                "* mise à jour des schémas de données *\n"+
                "**************************************");
        beanFactory.getBean(MigrateService.class);
        applicationRepository.findAll().stream()
                .forEach(app -> {
                    final MigrateService migrateService = beanFactory.getBean(MigrateService.class);
                    migrateService.setApplication(app);
                    migrateService.runFlywayUpdate();
                });
    }

    public int runFlywayUpdate() {
        authenticationService.resetRole();
        Flyway flyway = getFlyway();
        flyway.setCallbacks(new FlywayCallback());
        return flyway.migrate();
    }

    public Flyway getFlyway() {
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        ClassicConfiguration flywayConfiguration = new ClassicConfiguration();
        flywayConfiguration.setDataSource(dataSource);
        flywayConfiguration.setSchemas(sqlSchemaForApplication.getName());
        flywayConfiguration.setLocations(new Location("classpath:migration/application"));
        flywayConfiguration.getPlaceholders().put("applicationSchema", sqlSchemaForApplication.getSqlIdentifier());
        flywayConfiguration.getPlaceholders().put("requiredAuthorizations", sqlSchemaForApplication.requiredAuthorizationsAttributes(application));
        flywayConfiguration.getPlaceholders().put("requiredAuthorizationscomparing", sqlSchemaForApplication.requiredAuthorizationsAttributesComparing(application));
        Flyway flyway = new Flyway(flywayConfiguration);
        return flyway;
    }

    @FunctionalInterface
    interface ActionToDoAfterMigration {
        void execute(Connection connection) throws SQLException;
    }

    public class FlywayCallback implements Callback {

        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.AFTER_EACH_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(Event event, Context context) {
            return false;
        }

        @Override
        public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
            final String version = context.getMigrationInfo().getVersion().getVersion();
            Optional.ofNullable(callBackFunction.get(version))
                    .ifPresent(actionToDoAfterMigration -> {
                        try {
                            actionToDoAfterMigration.execute(connection);
                        } catch (SQLException e) {
                            log.log(Level.SEVERE, e.getMessage());
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private class Migrate1 implements ActionToDoAfterMigration {

        @Override
        public void execute(Connection connection) throws SQLException {
            log.info("--->migration 1");
            final Statement statement = connection.createStatement();

            SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
            OreSiRightOnApplicationRole adminOnApplicationRole = OreSiRightOnApplicationRole.adminOn(application);
            OreSiRightOnApplicationRole readerOnApplicationRole = OreSiRightOnApplicationRole.readerOn(application);
            OreSiRightOnApplicationRole writerOnApplicationRole = OreSiRightOnApplicationRole.writerOn(application);

            statement.execute(adminOnApplicationRole.toSqlCreaterole());
            statement.execute(readerOnApplicationRole.toSqlCreaterole());
            statement.execute(writerOnApplicationRole.toSqlCreaterole());
            statement.execute(readerOnApplicationRole.addUserInRoleSql(writerOnApplicationRole, false));

            //add  policies to adminOnApplicationRole for application
            statement.execute(new SqlPolicy(
                    String.join("_", adminOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.main().application(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    adminOnApplicationRole,
                    "name = '" + application.getName() + "'",
                    null
            ).policyToCreateSql());

            statement.execute(new SqlPolicy(
                    String.join("_", readerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
                    SqlSchema.main().application(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.SELECT),
                    readerOnApplicationRole,
                    "name = '" + application.getName() + "'",
                    null
            ).policyToCreateSql());


            statement.execute(new SqlPolicy(
                    String.join("_", writerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.INSERT.name()),
                    SqlSchema.main().application(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.INSERT),
                    writerOnApplicationRole,
                    null,
                    "name = '" + application.getName() + "'"
            ).policyToCreateSql());

            statement.execute(sqlSchemaForApplication.setSchemaOwnerSql( adminOnApplicationRole));
            statement.execute(sqlSchemaForApplication.setGrantToSql( readerOnApplicationRole));

            statement.execute(sqlSchemaForApplication.data().setTableOwnerSql( adminOnApplicationRole));
            statement.execute(sqlSchemaForApplication.referenceValue().setTableOwnerSql( adminOnApplicationRole));
            statement.execute(sqlSchemaForApplication.binaryFile().setTableOwnerSql( adminOnApplicationRole));
            statement.execute(sqlSchemaForApplication.authorization().setTableOwnerSql( adminOnApplicationRole));
            statement.execute(sqlSchemaForApplication.synthesis().setTableOwnerSql( adminOnApplicationRole));

            OreSiUserRole creator = authenticationService.getUserRole(request.getRequestUserId());
            statement.execute( adminOnApplicationRole.addUserInRoleSql(creator, false));
            statement.close();
            log.info("migration 1 --> ok");
        }
    }

    private class Migrate2 implements ActionToDoAfterMigration {

        @Override
        public void execute(Connection connection) throws SQLException {
            log.info("--->migration 2");
            SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
            OreSiRightOnApplicationRole adminOnApplicationRole = OreSiRightOnApplicationRole.adminOn(application);
            OreSiRightOnApplicationRole readerOnApplicationRole = OreSiRightOnApplicationRole.readerOn(application);
            final Statement statement = connection.createStatement();
            statement.execute(new SqlPolicy(
                    String.join("_", readerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
                    SqlSchema.forApplication(application).referenceValue(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.SELECT),
                    readerOnApplicationRole,
                    "application = '" + application.getId().toString() + "'::uuid",
                    null
            ).policyToCreateSql());
            statement.execute(sqlSchemaForApplication.authorizationReference().setTableOwnerSql( adminOnApplicationRole));
            statement.close();
            log.info("migration 2 --> ok");

        }

    }

    private class Migrate3 implements ActionToDoAfterMigration {

        @Override
        public void execute(Connection connection) throws SQLException {
            SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
            OreSiRightOnApplicationRole adminOnApplicationRole = OreSiRightOnApplicationRole.adminOn(application);
            final Statement statement = connection.createStatement();
            log.info("--->migration 3");
            statement.execute(new SqlPolicy(
                    String.join("_", adminOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).rightsRequest(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    adminOnApplicationRole,
                    "application = '" + application.getId().toString() + "'::uuid",
                    "application = '" + application.getId().toString() + "'::uuid"
            ).policyToCreateSql());
            statement.execute("CREATE POLICY \"" + application.getId().toString() +"\""+
                    "    ON " + SqlSchema.forApplication(application).rightsRequest().getSqlIdentifier() + "\n" +
                    "    AS PERMISSIVE\n" +
                    "    USING ( \"user\" = current_role::uuid)" +
                    "    WITH CHECK (\"user\" = current_role::uuid)");
            statement.execute(sqlSchemaForApplication.rightsRequest().setTableOwnerSql( adminOnApplicationRole));
            statement.close();

            log.info("migration 3 --> ok");
        }

    }
}