package fr.inra.oresing.rest.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("database.normalization")
@Slf4j
class NormalizedServiceTest {
    public final Fixtures.CreateUser monsoresimple = new Fixtures.CreateUser("monsoresimple", "xxxxxxxx", "monsoresimple@inrae.fr");
    public final Fixtures.CreateUser withRightsUser = new Fixtures.CreateUser("withrigths", "xxxxxxxx", "withrigths@inrae.fr");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonRowMapper jsonRowMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private NormalizedService normalizedService;
    private Fixtures fixtures;


    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(mockMvc, userRepository, namedParameterJdbcTemplate, authenticationService);
    }

    @TestFactory
    @DisplayName("Tests de la normalization de MONSOERE")
    public Stream<DynamicNode> testNormalization() {
        AtomicReference appId = new AtomicReference<>();
        MonSoereFixture monSoereFixture = new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);
        return Stream.of(
                dynamicContainer("initialisation des utilisateurs", Stream.of(dynamicTest("initialisation de l'utilisateur monsoresimple",
                                () -> {
                                    fixtures.monsoresimpleConnection = fixtures.createUserForUserDefinition(monsoresimple, true, false);
                                }),
                        dynamicTest("initialisation de l'utilisateur withRightsUser", () -> {
                            fixtures.withRightsUserConnection = fixtures.createUserForUserDefinition(withRightsUser, true, false);
                        }))),
                dynamicTest("test public", () -> {
                    monSoereFixture.testPublic();
                }),
                dynamicContainer("chargement de MONSOERE",
                        monSoereFixture.loadMonsore(appId)),
                dynamicContainer("chargement de MONSOERE",
                        buildNormalized("monsoresimple"))
        );

    }

    private Stream<? extends DynamicNode> buildNormalized(String applicationName) {

        return Stream.of(
                dynamicTest("dénormalization de mon %s".formatted(applicationName), () -> {
                    log.debug(applicationName);
                    mockMvc.perform(
                            MockMvcRequestBuilders.post("/api/v1/applications/{applicationName}/normalized", applicationName)
                                    .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt())
                    ).andExpect(status().isOk());
                }));
    }

    @Test
    public void normalizeMonsore() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/monsore/normalized/monsoereApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    @Test
    public void normalizeMultiplicity() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/multiplicity/normalized/multiplicityApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    @Test
    public void normalizePattern() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/pattern/normalized/patternApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }
}