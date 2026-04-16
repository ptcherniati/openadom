package fr.inra.oresing.domain.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import fr.inra.oresing.rest.services.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {OreSiNg.class})
@Tag("integration.migration")
public class ComparingConfiguration extends AbstractIntegrationTest {

    public static final String MIGRATION_CONFIGURATION_YAML = "/migration/configuration.yaml";
    public static final String TR_NEWREF = "tr_newref";

    @Autowired
    private MigrationService migrationService;

    private Application baseConfiguration;
    private ObjectMapper mapper;

    @BeforeEach
    public void init() {
        this.mapper = jsonRowMapper.getJsonMapper();
        this.baseConfiguration = loadConfiguration(MIGRATION_CONFIGURATION_YAML);
        insertApplicationInDb(baseConfiguration);
    }

    /**
     * Insère l'application dans la table Application via la connexion superuser
     * (même pattern que cleanDatabase dans AbstractIntegrationTest).
     * Nécessaire car buildContext() appelle findApplication() en base,
     * même en mode DRY_RUN.
     */
    private void insertApplicationInDb(Application application) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getMappedPort(5432), "test");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "test", "test")) {
            String configJson = mapper.writeValueAsString(application.getConfiguration());
            String[] dataArray = application.getData().toArray(new String[0]);

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO Application (id, name, data, configuration) " +
                    "SELECT gen_random_uuid(), ?, ?, ?::jsonb " +
                    "WHERE NOT EXISTS (SELECT 1 FROM Application WHERE name = ?)")) {
                stmt.setString(1, application.getConfiguration().applicationDescription().name());
                stmt.setArray(2, conn.createArrayOf("text", dataArray));
                stmt.setString(3, configJson);
                stmt.setString(4, application.getConfiguration().applicationDescription().name());
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Impossible d'insérer l'application de test en base", e);
        }
    }

    private Application loadConfiguration(String resourcePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Fichier non trouvé : " + resourcePath);
            }
            return mapper.readValue(inputStream, Application.class);
        } catch (IOException e) {
            throw new RuntimeException("Erreur chargement configuration: " + resourcePath, e);
        }
    }

    private Application createModifiedConfiguration(Consumer<Application> modifiedConfigBuilder)
            throws IOException, URISyntaxException {
        Application newApp = loadConfiguration(MIGRATION_CONFIGURATION_YAML);
        modifiedConfigBuilder.accept(newApp);
        return newApp;
    }

    @Test
    public void testAddNewReferenceType() throws IOException, URISyntaxException {
        Consumer<Application> modifiedConfigBuilder = (application) -> {
            application.getConfiguration().i18n().getData().put(TR_NEWREF, application.getConfiguration().i18n().getData().get("tr_theme_data_tda"));
            application.getConfiguration().dataDescription().put(TR_NEWREF, application.getConfiguration().dataDescription().get("tr_theme_data_tda"));
            application.getConfiguration().requiredAuthorizationsAttributes().add(TR_NEWREF);
            application.getData().add(TR_NEWREF);
        };
        Application modifiedConfig = createModifiedConfiguration(modifiedConfigBuilder);
        final MigrationResult result = migrationService.executeMigration(
                baseConfiguration,
                modifiedConfig,
                Set.of(),
                MigrationMode.DRY_RUN
        );
        assertNotNull(result, "Le résultat de migration ne doit pas être null");
    }
}