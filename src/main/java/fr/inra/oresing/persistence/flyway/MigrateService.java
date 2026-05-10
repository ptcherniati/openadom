package fr.inra.oresing.persistence.flyway;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiApplicationCreatorRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.index.AuthorizationIndex;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Component
@Slf4j
public class MigrateService {
    @Value("${SPRING_FLYWAY_PLACEHOLDERS_PUBLIC-ROLE-ID}")
    private String publicRoleId;
    @Autowired
    ApplicationRepository applicationRepository;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private DataSource dataSource;
    @Setter
    private Application application;
    @Autowired
    private BeanFactory beanFactory;

    public void migrateAll() {
        // Flyway main : idempotent at every boot . When a new V<N>__*.sql
        // is added under migration/main , Flyway compares the file set
        // against public.flyway_schema_history and only applies the deltas .
        // Already-installed migrations ( V1 + V2 historical ) are skipped
        // by Flyway itself ; new ones ( V6 + ) are applied generically on
        // every existing instance without manual psql intervention .
        //
        // validateOnMigrate = false : tolerates the legacy mismatch
        // observed on existing instances where flyway_schema_history . V2
        // carries description " oa metrics schema " while the current
        // V2__oa_audit_schema.sql in repo would otherwise raise a
        // checksum / description error . The version number remains the
        // contract that drives apply / skip decisions ; only the
        // historical body validation is relaxed . Each V<N>__ file uses
        // CREATE IF NOT EXISTS / ADD IF NOT EXISTS so a re-run on an
        // already-migrated object is a no-op ( defense in depth ) .
        final Flyway mainFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:migration/main")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .placeholders(Map.of("publicRoleId", publicRoleId))
                .load();

        List<Application> allSchemas;
        final MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        try {
            allSchemas = applicationRepository.findAll();
            // Public schema exists -> let Flyway apply any new V<N>__
            // detected since last boot ( idempotent no-op when up to date ) .
            mainFlyway.migrate();
        } catch (BadSqlGrammarException e) {
            log.info("""
                    \u001B[34m
                    ****************************************
                    * initialisation de la base de données *
                    ****************************************
                    \u001B[0m
                    """);
            mainFlyway.migrate();
            allSchemas = applicationRepository.findAll();
            log.info("""
                    \u001B[32m
                    ************************************
                    *      initialisation terminée     *
                    ************************************
                    \u001B[0m
                    """);
        }

        log.info("""
                \u001B[34m
                **************************************
                * mise à jour des schémas de données *
                **************************************
                \u001B[0m
                """);
        allSchemas
                .forEach(app -> {
                    migrateService.application = app;
                    log.info("->  \u001B[32m{}\u001B[0m  ...", app.getName());
                    migrateService.runFlywayUpdate(null);
                    log.info("... \u001B[32m{}\u001B[0m --> ok", app.getName());
                });
        log.info("""
                \u001B[32m
                **************************************
                *        migration terminée          *
                **************************************
                \u001B[0m
                """);
    }

    public void runFlywayUpdate(OreSiUserRole creator) {
        authenticationService.resetRole();
        final Flyway flyway = getFlyway(creator);
        MigrateResult migrate = flyway.migrate();
        updateAuthorizationIndexes(flyway);
    }

    private void updateAuthorizationIndexes(Flyway flyway) {
        long t0 = System.nanoTime();
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            AuthorizationIndex authorizationIndex = new AuthorizationIndex(application, null);

            // AUDIT 06-05-26 #3 option A : si tous les index attendus existent
            // deja en DB et qu aucun extra n est present , on skip totalement
            // le DROP + CREATE ( gain 99 % des boots sur grosses bases ) .
            // Le check coute 1 SELECT pg_indexes , vs N CREATE INDEX bloquants
            // qui posent un AccessExclusiveLock sur referencevalue .
            try {
                java.util.Set<String> expected = authorizationIndex.expectedIndexNames();
                java.util.Set<String> actual = new java.util.HashSet<>();
                String pgIdxSql = "SELECT indexname FROM pg_indexes "
                        + "WHERE schemaname = ? AND indexname LIKE 'authorization\\_%' ESCAPE '\\'";
                try (java.sql.PreparedStatement ps = connection.prepareStatement(pgIdxSql)) {
                    ps.setString(1, application.getName());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) actual.add(rs.getString(1));
                    }
                }
                if (actual.equals(expected)) {
                    long ms = (System.nanoTime() - t0) / 1_000_000L;
                    log.info("updateAuthorizationIndexes : skip ( {} index attendus tous presents en DB pour {} en {} ms )",
                            expected.size(), application.getName(), ms);
                    return;
                }
                java.util.Set<String> missing = new java.util.HashSet<>(expected);
                missing.removeAll(actual);
                java.util.Set<String> extra = new java.util.HashSet<>(actual);
                extra.removeAll(expected);
                log.info("updateAuthorizationIndexes : diff detecte pour {} ( manquants={} , obsoletes={} ) -> rebuild complet",
                        application.getName(), missing.size(), extra.size());
            } catch (SQLException diffErr) {
                log.warn("updateAuthorizationIndexes : diff pg_indexes a echoue ( fallback rebuild complet ) : {}",
                        diffErr.getMessage());
            }

            String createIndexesSql = authorizationIndex.createIndexes();
            try (Statement statement = connection.createStatement()) {
                statement.execute(createIndexesSql);
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            log.info("updateAuthorizationIndexes : rebuild complet termine pour {} en {} ms",
                    application.getName(), ms);
        } catch (SQLException e) {
            log.error("Erreur lors de la création des index d'autorisation pour l'application {}", application.getName(), e);
        }
    }

    private String getCurrentDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible d'obtenir l'utilisateur actuel de la base de données");
        }
        throw new IllegalStateException("Impossible d'obtenir l'utilisateur actuel de la base de données");
    }

    public Flyway getFlyway(OreSiUserRole creator) {
        final SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);
        final Map<String, ActionToDoAfterMigration> callBackFunction = new LinkedHashMap<>();
        callBackFunction.put("1", new Migrate1());

        return Flyway.configure()
                .dataSource(dataSource)
                .placeholders(Map.of(
                        "applicationSchema", sqlSchemaForApplication.getSqlIdentifier(),
                        "requiredAuthorizations", SqlSchemaForApplication.requiredAuthorizationsAttributes(application),
                        "requiredAuthorizationscomparing", SqlSchemaForApplication.requiredAuthorizationsAttributesComparing(application)
                ))
                .locations(new Location("classpath:migration/application"))
                .schemas(sqlSchemaForApplication.getName())
                .callbacks(
                        new SchemaFlywayCallback(
                                application,
                                getCurrentDatabase(),
                                authenticationService,
                                creator,
                                callBackFunction))
                .load();
    }

    public void updateSchema() {
        List<String> newRequiredAuthorizationAttributes = application.getConfiguration().requiredAuthorizationsAttributes();
        Application currentApplication = applicationRepository.findApplication(application.getId());
        List<String> currentRequiredAuthorizationAttributes = currentApplication.getConfiguration().requiredAuthorizationsAttributes();
        testHasDeletion(currentRequiredAuthorizationAttributes, newRequiredAuthorizationAttributes);
        testHasAddition(currentRequiredAuthorizationAttributes, newRequiredAuthorizationAttributes);
    }

    private void testHasAddition(List<String> currentRequiredAuthorizationAttributes, List<String> newRequiredAuthorizationAttributes) {
        boolean hasNoAddition = CollectionUtils.containsAll(currentRequiredAuthorizationAttributes, newRequiredAuthorizationAttributes);
        if (hasNoAddition) {
            return;
        }
        Collection<String> newDataIdentifiers = CollectionUtils.removeAll(
                newRequiredAuthorizationAttributes, currentRequiredAuthorizationAttributes
        );

        try {
            boolean added = applicationRepository.addReferenceToAuthorizationScope(application.getName(), newDataIdentifiers);
            log.info("""
                    Modification du schéma %1$s pour ajout d'identificateur de requiredAuthorizationScope.
                    Ajout des identificateurs %2$s.
                    Resultat : %3$s identificateurs ajoutés.
                    """
                    .formatted(application.getName(), newDataIdentifiers, added));
        } catch (Exception e) {
            throw new SiOreConfigurationFormatException(
                    ConfigurationException.ADDING_AUTHORIZATION_SCOPE_ATTRIBUTES_ERROR,
                    Map.of("newIdentifiers", newDataIdentifiers,
                            "message", e.getMessage())
            );
        }
    }

    private void testHasDeletion(List<String> currentRequiredAuthorizationAttributes, List<String> newRequiredAuthorizationAttributes) {
        boolean hasNoDeletion = CollectionUtils.containsAll(newRequiredAuthorizationAttributes, currentRequiredAuthorizationAttributes);
        if (hasNoDeletion) {
            return;
        }
        Collection<String> removingDataIdentifiers = CollectionUtils.removeAll(newRequiredAuthorizationAttributes, currentRequiredAuthorizationAttributes);

        throw new SiOreConfigurationFormatException(
                ConfigurationException.REMOVING_AUTHORIZATION_SCOPE_ATTRIBUTES_ERROR,
                Map.of("newIdentifiers", removingDataIdentifiers)
        );
    }

    @FunctionalInterface
    interface ActionToDoAfterMigration {
        void execute(Connection connection) throws SQLException;
    }

    public class FlywayCallback implements Callback {

        @Override
        public boolean supports(final Event event, final Context context) {
            return event == Event.AFTER_EACH_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(final Event event, final Context context) {
            return false;
        }

        @Override
        public void handle(final Event event, final Context context) {
            final Connection connection = context.getConnection();
            String version = context.getMigrationInfo().getVersion().getVersion();

            final Map<String, ActionToDoAfterMigration> callBackFunction = new LinkedHashMap<>();
            callBackFunction.put("1", new Migrate1());

            Optional.ofNullable(callBackFunction.get(version))
                    .ifPresent(actionToDoAfterMigration -> {
                        try {
                            actionToDoAfterMigration.execute(connection);
                        } catch (final SQLException e) {
                            log.error(e.getMessage());
                            throw new OreSiTechnicalException(ExceptionMessage.SQL_EXCEPTION.toMessage(), e);
                        }
                    });
        }

        @Override
        public String getCallbackName() {
            return "MigrateCallBack";
        }
    }

    private class Migrate1 implements ActionToDoAfterMigration {

        @Override
        public void execute(final Connection connection) throws SQLException {
            log.info("--->migration 1");
            Statement statement = connection.createStatement();
            OreSiApplicationCreatorRole applicationCreator = OreSiRole.applicationCreator();
            authenticationService.resetRole();
            final OreSiRightOnApplicationRole applicationManagerOnApplicationRole = OreSiRightOnApplicationRole.adminOn(application);
            final OreSiRightOnApplicationRole userManagerOnApplicationRole = OreSiRightOnApplicationRole.userAdminOn(application);



            /*
                select rights on authorization on schema application for anyUser
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "application", applicationManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
                    OreSiSqlSchema.authorization(SqlSchema.forApplication(application)),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.SELECT),
                    null,
                    "current_user::uuid = ANY(oreSiUsers::uuid[])",
                    null
            ).policyToCreateSql());

            /*
                set all on rightrequest to userManager
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "RR", applicationManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).rightsRequest(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    userManagerOnApplicationRole,
                    "(true)",
                    "(true)"
            ).policyToCreateSql());

            /*
                set all on rightrequest to userManager
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "RR", "current_user", SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).rightsRequest(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    null,
                    "\"user\"::text = current_user::text",
                    "\"user\"::text = current_user::text"
            ).policyToCreateSql());

            /*
                all on additionalBinaryFile for applicationId for all
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "ABF", "ownOrUpdated", SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).additionalBinaryFile(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    null,
                    "application = '" + application.getId().toString() + "'::uuid",
                    "application = '" + application.getId() + "'::uuid"
            ).policyToCreateSql());

            /*
                all on additionalBinaryFile for applicationId for all
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "ABF", "userManager", SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).additionalBinaryFile(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    userManagerOnApplicationRole,
                    "true",
                    "true"
            ).policyToCreateSql());

            /*
                all on binaryFile for usermanager
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "BF", userManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).binaryFile(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    userManagerOnApplicationRole,
                    "true",
                    "true"
            ).policyToCreateSql());

            /*
                all on authhorizations for usermanager
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "Auth", userManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).authorization(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    userManagerOnApplicationRole,
                    "true",
                    "true"
            ).policyToCreateSql());

            /*
                all on authhorizations for usermanager
             */
            statement.execute(new SqlPolicy(
                    String.join("_", "REFV", userManagerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                    SqlSchema.forApplication(application).referenceValue(),
                    SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                    List.of(SqlPolicy.Statement.ALL),
                    userManagerOnApplicationRole,
                    "true",
                    "true"
            ).policyToCreateSql());

            log.info("migration 1 --> ok");
            String indexesSQL = new AuthorizationIndex(application, null)
                    .createIndexes();
            statement.execute(indexesSQL);
            statement.close();

        }
    }
}