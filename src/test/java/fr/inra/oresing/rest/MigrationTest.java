package fr.inra.oresing.rest;

import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration.persistence")
@Slf4j
@SuppressWarnings("java:S2187") // testMigrate est volontairement désactivé (@Test commenté) — test d'intégration nécessitant un environnement complet
public class MigrationTest extends AbstractIntegrationTest {

    private String authJwt;

    @BeforeEach
    public void createApplication() throws Exception {
        fixtures = new Fixtures(
                mockMvc,
                userRepository,
                namedParameterJdbcTemplate,
                authenticationService
        );
        authJwt = fixtures.addMigrationApplication().jwt();
    }

    //@Test
    public void testMigrate() throws Exception {
        try (final InputStream configurationFile = getClass().getResourceAsStream(Fixtures.getMigrationApplicationConfigurationResourceName(2))) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "fake-app.yaml", "text/plain", configurationFile);
            fixtures.changeConfiguration(configuration, authJwt, "fakeapp", "fakeapp");
        }

        {
            final String actualCsv = mockMvc.perform(get("/api/v1/applications/fakeapp/data/jeu1/zip")
                            .header("Authorization", "Bearer " + authJwt)
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
    }
}