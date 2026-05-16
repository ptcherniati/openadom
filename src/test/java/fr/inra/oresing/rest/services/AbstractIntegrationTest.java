package fr.inra.oresing.rest.services;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.Fixtures;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Classe de base pour les tests d'intégration utilisant Docker/PostgreSQL.
 *
 * <p>Un <strong>unique</strong> conteneur PostgreSQL est démarré pour toute la classe
 * ({@code @BeforeAll}) et arrêté proprement à la fin ({@code @AfterAll}).
 * Entre chaque méthode de test, la base est remise à zéro via un nettoyage SQL
 * (suppression des schémas applicatifs, suppression + recréation de {@code public},
 * suppression des rôles applicatifs) plutôt que par un redémarrage du conteneur.
 * Flyway ré-exécute les migrations initiales au démarrage du nouveau contexte Spring
 * provoqué par {@code @DirtiesContext}.
 */
@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("docker-required")
public abstract class AbstractIntegrationTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    /** Identifiants du super-utilisateur créé par POSTGRES_USER dans le conteneur. */
    private static final String PG_SUPER_USER     = "test";
    private static final String PG_SUPER_PASSWORD = "test";
    private static final String PG_DATABASE       = "test";

    public static GenericContainer<?> postgres = new GenericContainer<>("postgres:18.3")
            .withEnv("POSTGRES_DB",       PG_DATABASE)
            .withEnv("POSTGRES_USER",     PG_SUPER_USER)
            .withEnv("POSTGRES_PASSWORD", PG_SUPER_PASSWORD)
            .withExposedPorts(5432)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("migration/openadom_user.sql"),
                    "/docker-entrypoint-initdb.d/openadom_user.sql"
            );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // preparedStatementCacheQueries=0 : évite les "cached plan must not change
        // result type" après la recréation des schémas entre deux tests.
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
                        + "/" + PG_DATABASE + "?preparedStatementCacheQueries=0");

        // ── Parallélisme auto-calibré sur le nombre de cœurs logiques ─────────
        // Auto-calibrage UNIQUEMENT si cascade.import.parallelism n'est pas déjà défini
        // (propriété système ou Spring) — on respecte la configuration explicite.
        // Règles de dimensionnement :
        //   • minimum 2 workers (évite le mode séquentiel sur petites CI)
        //   • maximum hikariCP test pool / 2 (application-testmail.yml : maximum-pool-size=20)
        //     → 10 connexions max pour les imports, 10 pour le reste (Flyway, diagnostics…)
        //   • plafonné à availableProcessors() : inutile d'avoir plus de workers que de cœurs
        //     sur du CPU-bound (validation Groovy, parsing CSV).
        //
        // Deux variables pilotées en parallèle :
        //   cascade.import.parallelism  → nb de chunks soumis simultanément au pool transform
        //   cascade.pool.transform      → taille du pool de threads cascade (system property)
        // Elles doivent être égales ; sinon les tâches s'exécutent en série dans le pool.
        if (System.getProperty("cascade.import.parallelism") == null
                && System.getenv("CASCADE_IMPORT_PARALLELISM") == null) {
            int logicalCores = Runtime.getRuntime().availableProcessors();
            int testParallelism = Math.clamp(logicalCores, 2, 10);

            // System property lue par ExecutionResourceManager (singleton cascade, JVM-level) :
            // doit être positionnée AVANT la première initialisation du contexte Spring.
            if (System.getProperty("cascade.pool.transform") == null) {
                System.setProperty("cascade.pool.transform", String.valueOf(testParallelism));
            }
            if (System.getProperty("cascade.pool.source") == null) {
                System.setProperty("cascade.pool.source", String.valueOf(Math.max(1, testParallelism / 2)));
            }
            if (System.getProperty("cascade.pool.sink") == null) {
                System.setProperty("cascade.pool.sink", String.valueOf(Math.max(1, testParallelism / 2)));
            }

            registry.add("cascade.import.parallelism", () -> testParallelism);
            log.info("[test-config] parallélisme auto-calibré : {} workers (logical cores={})",
                    testParallelism, logicalCores);
        } else {
            log.info("[test-config] parallélisme configuré explicitement, auto-calibrage ignoré");
        }
    }

    /** Démarre le conteneur une seule fois pour toute la classe de tests. */
    @BeforeAll
    static void beforeAll() {
        postgres.start();
        log.info("Conteneur PostgreSQL démarré (port {})", postgres.getMappedPort(5432));
    }

    /**
     * Arrête proprement le conteneur <em>une seule fois</em>, après tous les tests
     * de la classe. Remplace l'ancien pattern stop/start par test.
     */
    @AfterAll
    static void afterAll() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
            log.info("Conteneur PostgreSQL arrêté.");
        }
    }

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;

    @Autowired
    protected JsonRowMapper jsonRowMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    protected AuthenticationService authenticationService;

    protected Fixtures fixtures;

    @BeforeEach
    void baseSetUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        this.fixtures = new Fixtures(mockMvc, userRepository, namedParameterJdbcTemplate, authenticationService);
        logDatabaseDiagnostics("AVANT-TEST");
    }

    /**
     * Nettoie la base de données entre chaque test <em>sans</em> redémarrer le conteneur.
     *
     * <p>Stratégie de nettoyage (connexion en tant que super-utilisateur {@code test}) :
     * <ol>
     *   <li>Suppression de tous les schémas applicatifs (hors {@code public} et systèmes {@code pg_*}).</li>
     *   <li>Suppression puis recréation de {@code public} – efface la table
     *       {@code flyway_schema_history}, forçant Flyway à ré-exécuter les migrations
     *       lors du prochain démarrage du contexte Spring.</li>
     *   <li>Révocation de toutes les appartenances de rôles impliquant des rôles non essentiels
     *       (requis pour pouvoir ensuite les supprimer sans erreur de dépendance).</li>
     *   <li>Suppression de tous les rôles créés par Flyway ou l'application (rôles UUID,
     *       {@code openAdomAdmin}, {@code applicationCreator}, {@code anonymous}, etc.),
     *       en conservant uniquement {@code openAdomTechUser} et les rôles système.</li>
     * </ol>
     *
     * <p>Flyway ré-exécute ensuite {@code V1__init_schema.sql} sur le schéma {@code public}
     * vide, puis {@code MigrateService.migrateAll()} est appelé via
     * {@code ApplicationReadyEvent} – exactement comme au premier démarrage.
     */
    @AfterEach
    void cleanDatabase() {
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getMappedPort(5432), PG_DATABASE);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, PG_SUPER_USER, PG_SUPER_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // ── Préambule : terminer toutes les connexions actives (HikariCP, Flyway,
            //    subscriptions réactives fire-and-forget) pour prévenir les deadlocks
            //    de verrous avec DROP SCHEMA ... CASCADE.
            //    La connexion courante (cleanDatabase) est exclue via pg_backend_pid().
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT pg_terminate_backend(pid), pid, usename, state, left(query, 80) AS query
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid != pg_backend_pid()
                    """)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    log.debug("[cleanDatabase] connexion terminée: pid={} user={} state={} query={}",
                            rs.getInt("pid"), rs.getString("usename"),
                            rs.getString("state"), rs.getString("query"));
                }
                if (count > 0) {
                    log.info("[cleanDatabase] {} connexion(s) terminée(s) avant nettoyage", count);
                    // Attente active : on interroge pg_stat_activity jusqu'à ce que
                    // toutes les connexions soient effectivement fermées (ou timeout).
                    waitForConnectionsToDrain(conn, 5_000);
                }
            }

            // ── Étape 1 : supprimer tous les schémas applicatifs ─────────────────
            stmt.execute("""
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                        FOR r IN
                            SELECT nspname FROM pg_namespace
                            WHERE nspname NOT IN ('public', 'information_schema')
                              AND nspname NOT LIKE 'pg_%'
                        LOOP
                            EXECUTE 'DROP SCHEMA IF EXISTS ' || quote_ident(r.nspname) || ' CASCADE';
                        END LOOP;
                    END $$
                    """);

            // ── Étape 2 : supprimer et recréer public (efface l'historique Flyway) ─
            // public reste la propriété du database owner (test) : openAdomTechUser
            // n'obtient ses droits que via des grants, comme à l'initialisation du conteneur.
            stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
            stmt.execute("CREATE SCHEMA public");
            stmt.execute("GRANT USAGE, CREATE ON SCHEMA public TO \"openAdomTechUser\"");
            // Reproduire le grant implicite des bases PostgreSQL fraîches : depuis PG 15,
            // PUBLIC n'a plus USAGE sur public par défaut. Sans ce grant, les rôles UUID
            // créés par Flyway (membres de openAdomAdmin) obtiennent "permission denied
            // for schema public" car V1__init_schema.sql ne s'est pas encore exécuté.
            stmt.execute("GRANT USAGE ON SCHEMA public TO PUBLIC");

            // ── Étape 3 : révoquer les appartenances impliquant des rôles non essentiels ─
            stmt.execute("""
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                        FOR r IN
                            SELECT m.rolname AS member_role, g.rolname AS group_role
                            FROM pg_auth_members am
                            JOIN pg_roles m ON m.oid = am.member
                            JOIN pg_roles g ON g.oid = am.roleid
                            WHERE (    m.rolname NOT IN ('postgres', 'test', 'openAdomTechUser')
                                   AND m.rolname NOT LIKE 'pg_%')
                               OR (    g.rolname NOT IN ('postgres', 'test', 'openAdomTechUser')
                                   AND g.rolname NOT LIKE 'pg_%')
                        LOOP
                            BEGIN
                                EXECUTE format('REVOKE %I FROM %I', r.group_role, r.member_role);
                            EXCEPTION WHEN OTHERS THEN NULL;
                            END;
                        END LOOP;
                    END $$
                    """);

            // ── Étape 4 : supprimer tous les rôles applicatifs non essentiels ────
            stmt.execute("""
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                        FOR r IN
                            SELECT rolname FROM pg_roles
                            WHERE rolname NOT IN ('postgres', 'test', 'openAdomTechUser')
                              AND rolname NOT LIKE 'pg_%'
                        LOOP
                            BEGIN
                                EXECUTE 'DROP OWNED BY ' || quote_ident(r.rolname);
                                EXECUTE 'DROP ROLE IF EXISTS ' || quote_ident(r.rolname);
                            EXCEPTION WHEN OTHERS THEN
                                RAISE NOTICE 'Impossible de supprimer le rôle % : %',
                                             r.rolname, SQLERRM;
                            END;
                        END LOOP;
                    END $$
                    """);

            log.debug("Base de données nettoyée (port {})", postgres.getMappedPort(5432));
            logDatabaseDiagnostics("APRÈS-NETTOYAGE");

        } catch (SQLException e) {
            log.error("Erreur lors du nettoyage de la base de données entre les tests", e);
            throw new RuntimeException("Échec du nettoyage de la base de données", e);
        }
    }

    /**
     * Attend activement que toutes les connexions autres que la connexion courante
     * soient fermées sur la base de données, sans utiliser {@code Thread.sleep}.
     *
     * <p>Interroge {@code pg_stat_activity} par paliers de 50 ms via
     * {@link LockSupport#parkNanos(long)} jusqu'à ce que le compteur tombe à 0
     * ou que {@code maxWaitMs} soit écoulé.
     *
     * @param conn      connexion active (super-utilisateur) — réutilisée pour éviter une
     *                  nouvelle ouverture de connexion
     * @param maxWaitMs délai maximum d'attente en millisecondes
     */
    private void waitForConnectionsToDrain(Connection conn, long maxWaitMs) throws SQLException {
        final long pollIntervalNs = TimeUnit.MILLISECONDS.toNanos(50);
        final long deadlineNs     = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxWaitMs);

        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT count(*)
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND pid != pg_backend_pid()
                """)) {

            while (true) {
                int remaining;
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    remaining = rs.getInt(1);
                }
                if (remaining == 0) {
                    log.debug("[cleanDatabase] toutes les connexions sont fermées");
                    return;
                }
                if (System.nanoTime() >= deadlineNs) {
                    log.warn("[cleanDatabase] {} connexion(s) toujours active(s) après {}ms — DROP peut échouer",
                            remaining, maxWaitMs);
                    return;
                }
                log.debug("[cleanDatabase] {} connexion(s) en cours de fermeture, nouvelle vérification dans 50ms",
                        remaining);
                LockSupport.parkNanos(pollIntervalNs);
            }
        }
    }

    /**
     * Requête la base de données pour logger l'état courant : rôles PG, schémas, grants
     * sur {@code public.Application} et connexion active. Utile pour diagnostiquer les
     * problèmes de droits entre les tests.
     *
     * @param phase libellé affiché en tête de log (ex. {@code "AVANT-TEST"}, {@code "APRÈS-NETTOYAGE"})
     */
    protected void logDatabaseDiagnostics(String phase) {
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getMappedPort(5432), PG_DATABASE);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, PG_SUPER_USER, PG_SUPER_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // ── Utilisateur de la connexion courante ─────────────────────────────
            try (ResultSet rs = stmt.executeQuery("SELECT current_user, session_user, current_database()")) {
                if (rs.next()) {
                    log.debug("[{}] Connexion PG → current_user={}, session_user={}, database={}",
                            phase, rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }

            // ── Rôles applicatifs (hors rôles système pg_*) ──────────────────────
            StringBuilder roles = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT rolname, rolcanlogin, rolcreaterole " +
                    "FROM pg_roles WHERE rolname NOT LIKE 'pg_%' ORDER BY rolname")) {
                while (rs.next()) {
                    roles.append(String.format("%n    %-45s login=%-5s createrole=%s",
                            rs.getString("rolname"),
                            rs.getBoolean("rolcanlogin"),
                            rs.getBoolean("rolcreaterole")));
                }
            }
            log.debug("[{}] Rôles PG :{}", phase, roles.length() > 0 ? roles : " (aucun)");

            // ── Appartenances de rôles (membres → groupes) ───────────────────────
            StringBuilder memberships = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT m.rolname AS member, g.rolname AS grp " +
                    "FROM pg_auth_members am " +
                    "JOIN pg_roles m ON m.oid = am.member " +
                    "JOIN pg_roles g ON g.oid = am.roleid " +
                    "WHERE m.rolname NOT LIKE 'pg_%' AND g.rolname NOT LIKE 'pg_%' " +
                    "ORDER BY m.rolname, g.rolname")) {
                while (rs.next()) {
                    memberships.append(String.format("%n    %s → %s",
                            rs.getString("member"), rs.getString("grp")));
                }
            }
            log.debug("[{}] Appartenances de rôles :{}", phase,
                    memberships.length() > 0 ? memberships : " (aucune)");

            // ── Schémas existants ────────────────────────────────────────────────
            StringBuilder schemas = new StringBuilder();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT nspname, pg_get_userbyid(nspowner) AS owner " +
                    "FROM pg_namespace " +
                    "WHERE nspname NOT LIKE 'pg_%' AND nspname <> 'information_schema' " +
                    "ORDER BY nspname")) {
                while (rs.next()) {
                    schemas.append(String.format("%n    %s  (owner=%s)",
                            rs.getString("nspname"), rs.getString("owner")));
                }
            }
            log.debug("[{}] Schémas PG :{}", phase, schemas.length() > 0 ? schemas : " (aucun)");

            // ── Grants sur public.Application (si la table existe) ───────────────
            try (ResultSet tableExists = stmt.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name = 'application')")) {
                tableExists.next();
                if (tableExists.getBoolean(1)) {
                    StringBuilder grants = new StringBuilder();
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT grantee, privilege_type " +
                            "FROM information_schema.role_table_grants " +
                            "WHERE table_schema = 'public' AND table_name = 'application' " +
                            "ORDER BY grantee, privilege_type")) {
                        while (rs.next()) {
                            grants.append(String.format("%n    %-40s %s",
                                    rs.getString("grantee"), rs.getString("privilege_type")));
                        }
                    }
                    log.debug("[{}] Grants sur public.application :{}", phase,
                            grants.length() > 0 ? grants : " (aucun)");

                    // ── Politique RLS active sur Application ─────────────────────
                    StringBuilder policies = new StringBuilder();
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT policyname, cmd, roles, qual " +
                            "FROM pg_policies " +
                            "WHERE schemaname = 'public' AND tablename = 'application'")) {
                        while (rs.next()) {
                            policies.append(String.format("%n    [%s] cmd=%s roles=%s qual=%s",
                                    rs.getString("policyname"),
                                    rs.getString("cmd"),
                                    rs.getString("roles"),
                                    rs.getString("qual")));
                        }
                    }
                    log.debug("[{}] Politiques RLS sur public.application :{}", phase,
                            policies.length() > 0 ? policies : " (aucune)");
                } else {
                    log.warn("[{}] Table public.application ABSENTE — Flyway n'a pas encore tourné ?", phase);
                }
            }

        } catch (SQLException e) {
            log.warn("[{}] Impossible de récupérer les diagnostics DB : {}", phase, e.getMessage());
        }
    }
}