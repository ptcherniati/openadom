package fr.inra.oresing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

// Singleton pattern pour Testcontainers : un seul container PostgreSQL partagé par tous les tests
// exécutés dans la même JVM. En CI, chaque job ( ex: test_core_auth, test_integration_rest )
// tourne dans sa propre JVM avec son propre Docker DinD - les jobs restent isolés entre eux.
//
// Raison :
//   Chaque classe de test avec @DirtiesContext recrée le contexte Spring, ce qui rappelait
//   dataSource() et créait un NOUVEAU container PostgreSQL sans arrêter le précédent.
//   Avec 3+ classes dans un même profil Maven, les containers s'accumulaient et les ports
//   ou connexions entraient en conflit -> "Failed to initialize database user".
//
// Fonctionnement :
//   Le container est créé dans un bloc static {} -> démarré UNE SEULE FOIS au chargement
//   de la classe, puis réutilisé par tous les appels à dataSource().
//   La JVM arrête le container automatiquement à la fin de tous les tests (via shutdown hook
//   intégré à Testcontainers).
@TestConfiguration
public class TestDatabaseConfig {

    // Container singleton : démarré une seule fois, partagé par toutes les classes de test
    private static final GenericContainer<?> POSTGRES;
    private static final String JDBC_URL;

    static {
        POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:18.0"))
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", "test")
                .withEnv("POSTGRES_USER", "postgres")
                .withEnv("POSTGRES_PASSWORD", "postgres");

        POSTGRES.start();

        JDBC_URL = String.format(
                "jdbc:postgresql://%s:%d/test",
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(5432)
        );

        // Exécuter le script d'initialisation une seule fois
        DriverManagerDataSource initDataSource = new DriverManagerDataSource();
        initDataSource.setDriverClassName("org.postgresql.Driver");
        initDataSource.setUrl(JDBC_URL);
        initDataSource.setUsername("postgres");
        initDataSource.setPassword("postgres");

        try (Connection conn = initDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String scriptPath = "src/test/resources/migration/openadom_user.sql";
            String scriptContent = Files.readString(Paths.get(scriptPath), StandardCharsets.UTF_8);
            // Exécuter chaque commande SQL séparément (Statement.execute ne supporte
            // pas toujours plusieurs commandes séparées par ";")
            for (String sql : scriptContent.split(";")) {
                // Retirer les lignes de commentaire (--) pour isoler le SQL pur
                String cleaned = java.util.Arrays.stream(sql.split("\n"))
                        .filter(line -> !line.trim().startsWith("--"))
                        .collect(java.util.stream.Collectors.joining("\n"))
                        .trim();
                if (!cleaned.isEmpty()) {
                    stmt.execute(cleaned);
                }
            }
        } catch (Exception e) {
            POSTGRES.stop();
            throw new RuntimeException("Failed to initialize database user", e);
        }
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        // Réutilise le container singleton déjà démarré
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(JDBC_URL);
        dataSource.setUsername("openAdomTechUser");
        dataSource.setPassword("z2I<i}qclq)D?xqT");
        return dataSource;
    }
}
