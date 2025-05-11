package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("testmail")
@Tag("integration.persistence")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
public class MigrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    private Cookie authCookie;

    @BeforeEach
    public void createApplication() throws Exception {
        authCookie = fixtures.addMigrationApplication();
    }

    @Test
    @Disabled
    public void testMigrate() throws Exception {
        try (final InputStream configurationFile = getClass().getResourceAsStream(Fixtures.getMigrationApplicationConfigurationResourceName(2))) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "fake-app.yaml", "text/plain", configurationFile);
            fixtures.changeConfiguration(configuration, authCookie, "fakeapp", "fakeapp");
        }

        {
            final String actualCsv = mockMvc.perform(get("/api/v1/applications/fakeapp/data/jeu1/zip")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
    }
}