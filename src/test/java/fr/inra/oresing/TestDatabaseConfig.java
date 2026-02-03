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

@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:18.0"))
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", "test")
                .withEnv("POSTGRES_USER", "postgres")
                .withEnv("POSTGRES_PASSWORD", "postgres");

        postgres.start();

        // Récupérer l'URL JDBC
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%d/test",
                postgres.getHost(),
                postgres.getMappedPort(5432)
        );

        // Initialiser la base de données avec le user postgres
        DriverManagerDataSource initDataSource = new DriverManagerDataSource();
        initDataSource.setDriverClassName("org.postgresql.Driver");
        initDataSource.setUrl(jdbcUrl);
        initDataSource.setUsername("postgres");
        initDataSource.setPassword("postgres");
        
        // Exécuter le script d'initialisation
        try (Connection conn = initDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String scriptPath = "src/test/resources/migration/openadom_user.sql";
            String scriptContent = Files.readString(Paths.get(scriptPath), StandardCharsets.UTF_8);
            stmt.execute(scriptContent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database user", e);
        }

        // Retourner un DataSource avec l'utilisateur openAdomTechUser
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("openAdomTechUser");
        dataSource.setPassword("z2I<i}qclq)D?xqT");

        return dataSource;
    }
}