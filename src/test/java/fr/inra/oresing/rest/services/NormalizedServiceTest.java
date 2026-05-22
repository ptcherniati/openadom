package fr.inra.oresing.rest.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Tag("docker-required")
class NormalizedServiceTest extends AbstractIntegrationTest {
    public final Fixtures.CreateUser monsoresimple = new Fixtures.CreateUser("monsoresimple", "xxxxxxxx", "monsoresimple@inrae.fr");
    public final Fixtures.CreateUser withRightsUser = new Fixtures.CreateUser("withrigths", "xxxxxxxx", "withrigths@inrae.fr");

    @Autowired
    private NormalizedService normalizedService;


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
                dynamicTest("test public", monSoereFixture::testPublic),
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
    @SuppressWarnings("java:S2699") // vérifie l'absence d'exception — pas de valeur à asserter
    public void normalizeMonsore() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/monsore/normalized/monsoereApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, false)
                    .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    @Test
    @SuppressWarnings("java:S2699") // vérifie l'absence d'exception — pas de valeur à asserter
    public void normalizeMultiplicity() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/multiplicity/normalized/multiplicityApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                    .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    @Test
    @SuppressWarnings("java:S2699") // vérifie l'absence d'exception — pas de valeur à asserter
    public void normalizePattern() {
        try (final InputStream applicationStream = getClass().getResourceAsStream("/data/pattern/normalized/patternApplication.json")) {
            jsonRowMapper.getJsonMapper()
                    .configure(READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                    .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
            Application application = (Application) jsonRowMapper.readStream(applicationStream, Application.class);
            normalizedService.buildNormalizedSchema(application, false);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }
}