package fr.inra.oresing;

import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Configuration de DataSource pour les tests qui ont besoin d'importer
 * explicitement un bean DataSource (via {@code @Import(TestDatabaseConfig.class)}).
 *
 * <p>Cette classe réutilise le conteneur singleton géré par
 * {@link AbstractIntegrationTest} – il ne crée <strong>pas</strong> un nouveau
 * conteneur, évitant ainsi la prolifération des conteneurs non fermés.
 *
 * <p><strong>Note :</strong> la plupart des tests d'intégration héritent de
 * {@link AbstractIntegrationTest} et n'ont pas besoin d'importer cette classe.
 */
@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        // Réutilise le conteneur déjà démarré par AbstractIntegrationTest
        String jdbcUrl = String.format(
                "jdbc:postgresql://%s:%d/test?preparedStatementCacheQueries=0",
                AbstractIntegrationTest.postgres.getHost(),
                AbstractIntegrationTest.postgres.getMappedPort(5432)
        );

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername("openAdomTechUser");
        dataSource.setPassword("z2I<i}qclq)D?xqT");
        return dataSource;
    }
}
