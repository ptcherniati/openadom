package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.reactive.*;
import fr.inra.oresing.rest.services.ApplicationConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
@org.junit.jupiter.api.Tag("SUITE")
@org.junit.jupiter.api.Tag("core.config")
public class ApplicationConfigurationServiceTest {

    public static final Map<String, List<ReactiveResult>> errors = new HashMap<>();
    protected TestConfigurationBuilder CONFIGURATION_INSTANCE;
    @Autowired
    private Fixtures fixtures;
    @Autowired
    private ApplicationConfigurationService service;

    @AfterAll
    public static void registerErrors() throws IOException {
        final JsonRowMapper jsonMapper = new JsonRowMapper<>();
        final String errorsToJson = jsonMapper
                .toJson(errors);
        try (
                final PrintWriter writerTxt = new PrintWriter("ui/cypress/fixtures/applications/errors/errors.json", StandardCharsets.UTF_8)
        ) {
            writerTxt.write(errorsToJson);
        }
    }

    private static Flux<ReactiveResult> buildFluxRequestJDJson(final Consumer<FluxSink<ReactiveResult>> fluxSink) {
        return Flux.create(fluxSink);
    }

    @Test
    public void multiplesErrors() {
        CONFIGURATION_INSTANCE.builder("testReturnMultiplesErrors")
                .withReplace("  sites:", "  site:")
                .test(errors -> assertTrue(errors.size() > 1));
    }

    @BeforeEach

    public void before() {
        CONFIGURATION_INSTANCE = new TestConfigurationBuilder();
    }

    @Test
    public void parseConfigurationFile() {
        List<String> block = Collections.singletonList(buildFluxRequestJDJson(fluxSink -> {
            ImmutableSet<String> configFiles = ImmutableSet.of(
                    Fixtures.getAcbbApplicationConfigurationResourceName(),
                    Fixtures.getMonsoreApplicationConfigurationResourceName(),
                    Fixtures.getRecursivityApplicationConfigurationResourceName(),
                    Fixtures.getMonsoreApplicationConfigurationWithRepositoryResourceName(),
                    Fixtures.getPatternApplicationConfigurationResourceName()
                    //Fixtures.getOlaApplicationConfigurationResourceName(),
                    //Fixtures.getHauteFrequenceApplicationConfigurationResourceName(),
                    //Fixtures.getValidationApplicationConfigurationResourceName()
            );
            for (String resourceName : configFiles) {
                parseConfigurationFromResource(resourceName, fluxSink);
            }

            ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(
                    new ReactiveProgression.DefaultCounter(0L),
                    fluxSink,
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel()
            );

            // Tests avec différentes configurations
            try {
                testConfiguration(progression, "version: 0", false);
                testConfiguration(progression, "version: 1", true);
                testConfiguration(progression, "version: 2", false);
                testConfiguration(progression, "::", false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            fluxSink.complete();
        })
                .filter(reactiveResult -> reactiveResult.type().equals(ReactiveType.REACTIVE_ERROR) || reactiveResult.type().equals(ReactiveType.REACTIVE_RESULT))

                .filter(ReactiveTypeResult.class::isInstance)
                .map(ReactiveTypeResult.class::cast)
                .map(ReactiveTypeResult::result)
                .filter(Application.class::isInstance)
                .map(Application.class::cast)
                .map(Application::getConfiguration)
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::version)
                .map(Version::version)
                .collectList()
                .block()
                .stream().collect(Collectors.joining("\n")));
        assertEquals("""
                1.0.5
                3.0.1
                3.0.1
                3.0.1
                3.0.1""",
                block.get(0));
    }

    private void parseConfigurationFromResource(String resource, FluxSink<ReactiveResult> fluxSink) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(
                    new ReactiveProgression.DefaultCounter(0L),
                    fluxSink,
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel()
            );

            FileBomResolver fileBomResolver = FileBomResolver.of(in);
            byte[] configBytes = fileBomResolver.readAllBytes();

            Application application = ApplicationConfigurationService.parseConfigurationBytes(
                    "test",
                    progression,
                    FileBomResolver.of(configBytes)
            );
            assertNotNull(application, "L'application ne devrait pas être nulle pour " + resource);
            progression.pushResult(application);
        } catch (IOException e) {
            fail("Impossible de lire le fichier de test " + resource + ": " + e.getMessage());
        }
    }

    private void testConfiguration(ReactiveProgression.CreateApplicationProgression progression, String config, boolean expectedValidity) throws IOException {
        byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
        FileBomResolver fileBomResolver = FileBomResolver.of(new ByteArrayInputStream(configBytes));
        Application application = ApplicationConfigurationService.parseConfigurationBytes("", progression, fileBomResolver);
        System.out.println(application);
        //assertEquals(expectedValidity, application.isValid(), "La configuration '" + config + "' devrait être " + (expectedValidity ? "valide" : "invalide"));
    }


    private void parseConfigurationFromResource(final String resource) {
        buildFluxRequestJDJson(fluxSink -> {
            final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(new ReactiveProgression.DefaultCounter(0L), fluxSink, new ReactiveProgression.CreateApplicationProgressionMessagesLabel());

            final Application errors;
            try (final InputStream in = getClass().getResourceAsStream(resource)) {
                ApplicationConfigurationService.parseConfigurationBytes("test", progression, FileBomResolver.of(in));
                //TODO
                //assertTrue(() -> errors.isEmpty(), resource + " doit être reconnu comme un fichier valide");
            } catch (final IOException e) {
                throw new OreSiTechnicalException("ne peut pas lire le fichier de test " + resource, e);
            }
            fluxSink.complete();
        });
    }

    @Test
    public void testBadBuilderVersion() {
        CONFIGURATION_INSTANCE.builder("testBadBuilderVersion")
                .withReplace("""
                        OA_version: 2.0.1
                        OA_application:""", """
                        OA_version: 2
                        OA_application:""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNSUPPORTED_OPENADOM_VERSION.getMessage(), validationError.getMessage());
                    assertEquals(ConfigurationSchemaNode.OA_VERSION, validationError.getParam("path"));
                    assertEquals("2", validationError.getParam("actualVersion"));
                    assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParam("expectedVersion"));
                });
    }

    @Test
    public void testBadDomaineTagPattern() {
        CONFIGURATION_INSTANCE.builder("testBadDomaineTagPattern")
                .withReplace("""
                        context:""", """
                        context-:""")
                .test(errors -> {
                    assertEquals(11, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_DOMAIN_TAG_PATTERN.getMessage(), validationError.getMessage());
                    assertEquals(ConfigurationSchemaNode.OA_TAGS, validationError.getParam("path"));
                    assertEquals(Tag.DomainTag.DOMAIN_PATTERN, validationError.getParam("domainTagPattern"));
                });
    }

    @Test
    public void testBadNameApplication() {
        CONFIGURATION_INSTANCE.builder("testBadNameApplication")
                .withReplace("""
                        OA_name: fake_application""", """
                        OA_name: F4KE app!cat°""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNSUPPORTED_NAME_APPLICATION.getMessage(), validationError.getMessage());
                    assertEquals(ConfigurationSchemaNode.OA_APPLICATION, validationError.getParam("path"));
                    assertEquals("F4KE app!cat°", validationError.getParam("nameApplication"));
                });
    }

    @Test
    public void testBadNameTag() {
        CONFIGURATION_INSTANCE.builder("testBadNameTag")
                .withReplace("""
                        OA_tags: [ context ]""", """
                        OA_tags: [ context_ ]""")
                .test(errors -> {
                    assertEquals(3, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_TAGS_PATTERNS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes", validationError.getParam("path"));
                    assertEquals(Set.of("__HIDDEN__", "__REFERENCE__", "test", "context", "no-tag", "__ORDER_(\\d*)__", "__DATA__"), validationError.getParam(("acceptedTagPatterns")));
                });
    }

    @Test
    public void testBadNameTagInDynamicComponents() {
        CONFIGURATION_INSTANCE.builder("testBadNameTagInDynamicComponents")
                .withReplace("""
                        OA_tags: [ test, context ]""", """
                        OA_tags: [ test_, context ]""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_TAGS_PATTERNS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_tags", validationError.getParam("path"));
                    assertEquals(Set.of("__HIDDEN__", "__REFERENCE__", "test", "context", "no-tag", "__ORDER_(\\d*)__", "__DATA__"), validationError.getParam(("acceptedTagPatterns")));
                });
    }

    @Test
    public void testBadReferenceNameForChecker() {
        CONFIGURATION_INSTANCE.builder("testMissingReferenceNameForChecker")
                .withReplace("""
                                          OA_params:
                                            OA_reference:
                                              OA_name: type_de_sites
                                              OA_isParent: true\
                                """,
                        """
                                          OA_params:
                                            OA_reference:
                                              OA_name: toto
                                              OA_isParent: true\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                    final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                });
    }

    @Test
    public void testBadVersionApplication() {
        CONFIGURATION_INSTANCE.builder("testBadVersionApplication")
                .withReplace("""
                        OA_version: 3.0.1""", """
                        OA_version: -2""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_VERSION_PATTERN.getMessage(), validationError.getMessage());
                    assertEquals("-2", validationError.getParam("givenVersion"));
                    assertEquals(ConfigurationSchemaNode.OA_APPLICATION, validationError.getParam("path"));
                });
    }

    @Test
    public void testEmptyFile() {
        CONFIGURATION_INSTANCE
                .builder("testEmptyFile", "emptyConfigurationFile.yaml")
                .test(errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.EMPTY_FILE.getMessage(), validationError.getMessage());
                            assertEquals("emptyFile", ConfigurationException.EMPTY_FILE.getMessage());
                        }
                );
    }

    @Test
    public void testInvalidDurationForCheckerDate() {
        CONFIGURATION_INSTANCE.builder("testInvalidDurationForCheckerDate")
                .withReplace("          OA_name: OA_date\n" +
                                "          OA_params:",
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_duration: 1 Yearss\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.INVALID_DURATION_CHECKER_DATE.getMessage(), validationError.getMessage());
                    assertEquals("1 Yearss", validationError.getParam("declaredDuration"));
                    assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                });
    }

    @Test
    public void testInvalidMinForCheckerDate() {
        CONFIGURATION_INSTANCE.builder("testInvalidMinMaxForCheckerDate")
                .withReplace("""
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy
                                            OA_min: 12/31/1980
                                            OA_max: 31/12/2024\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.INVALID_MIN_MAX_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                    assertEquals("12/31/1980", validationError.getParam("declaredMinValue"));
                    assertEquals("31/12/2024", validationError.getParam("declaredMaxValue"));
                    assertEquals("dd/MM/yyyy", validationError.getParam("declaredPattern"));
                    assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                });
    }

    @Test
    public void testInvalidMaxForCheckerDate() {
        CONFIGURATION_INSTANCE.builder("testInvalidMinMaxForCheckerDate")
                .withReplace("""
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy
                                            OA_min: 31/12/1980
                                            OA_max: 12/31/2024\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.INVALID_MIN_MAX_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                    assertEquals("31/12/1980", validationError.getParam("declaredMinValue"));
                    assertEquals("12/31/2024", validationError.getParam("declaredMaxValue"));
                    assertEquals("dd/MM/yyyy", validationError.getParam("declaredPattern"));
                    assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                });
    }

    @Test
    public void testInvalidNaturalKey() {
        CONFIGURATION_INSTANCE.builder("testInvalidNaturalKey")
                .withReplace("""
                        - esp_nom""", """
                        - espNom""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.INVALID_NATURAL_KEY.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes", validationError.getParam(("path")));
                    assertEquals(Set.of("espNom"), validationError.getParam("invalidNaturalKeyElements"));
                    final Set<String> expected = Arrays.stream(new String[]{"esp_nom", "esp_definition_fr", "esp_definition_en", "colonne_homonyme_entre_referentiels", "my_computed_column"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = (Set<String>) validationError.getParam("expectedComponentLabel");
                    assertEquals(expected, given);
                });
    }

    @Test
    public void testInvalidPatternForCheckerDate() {
        CONFIGURATION_INSTANCE.builder("testInvalidPatternForCheckerDate")
                .withReplace("""
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: bb/MM/yyyy\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                    assertEquals("bb/MM/yyyy", validationError.getParam("badPattern"));
                    assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params > OA_pattern", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingBuilderVersion() {
        CONFIGURATION_INSTANCE.builder("testMissingBuilderVersion")
                .withReplace("""
                        OA_version: 2.0.1
                        OA_application:""", """
                        OA_version:
                        OA_application:""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_VERSION_APPLICATION.getMessage(), validationError.getMessage());
                    assertEquals(ConfigurationSchemaNode.OA_VERSION, validationError.getParam("path"));
                    assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParam("expectedVersion"));
                });
    }

    @Test
    public void testMissingCheckerName() {
        CONFIGURATION_INSTANCE.builder("testMissingNameChecker")
                .withReplace("""
                                      tze_type_nom:
                                        OA_required: true
                                        OA_checker:
                                          OA_name: OA_reference\
                                """,
                        """
                                      tze_type_nom:
                                        OA_required: true
                                        OA_checker:
                                          OA_name:\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_CHECKER_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom", validationError.getParam(("path")));
                    final Set<String> expected = Arrays.stream(new String[]{"OA_reference", "OA_boolean", "OA_date", "OA_integer", "OA_float", "OA_string", "OA_groovyExpression"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = (Set<String>) validationError.getParam("acceptedCheckerNames");
                    assertEquals(expected, given);
                });
    }

    @Test
    public void testMissingAnyMandatorySectionsInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testMissingAnyMandatorySectionsInConstantComponents")
                .withReplace("OA_columnName: \"site\"",
                        "")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_ANY_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                    final Set<String> expectedComponents = Arrays.stream(new String[]{"OA_columnName", "OA_columnNumber"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> givenComponents = (TreeSet<String>) validationError.getParam("anyMandatorySections");
                    assertEquals(expectedComponents, givenComponents);
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_importHeaderTarget > OA_rowNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testmissingRequiredValueInTimeScopeInSubmission() {
        CONFIGURATION_INSTANCE.builder("testmissingRequiredValueInTimeScopeInSubmission")
                .withReplace("        OA_timeScope:\n" +
                                "          OA_component: date",
                        "        OA_timeScope:\n" +
                                "          OA_component: ")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_timeScope > OA_component", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingAnyMandatoriesSectionsForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testMissingAnyMandatoriesSectionsForAuthorization")
                .withReplace("            OA_reference: projet\n" +
                                "            OA_component: projet",
                        "")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                    final Set<String> expectedComponents = Arrays.stream(new String[]{"OA_component", "OA_reference"})
                            .collect(Collectors.toSet());
                    final Set<String> givenComponents = (Set<String>) validationError.getParam("missingMandatoriesSections");
                    assertEquals(expectedComponents, givenComponents);
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_exportHeader > OA_i18n", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingComponentNameForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testmissingComponentNameForAuthorization")
                .withReplace("            OA_reference: projet\n" +
                                "            OA_component: projet",
                        "            OA_reference: projet\n" +
                                "            OA_component:")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_component", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingComponentNameInColumnsForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testMissingComponentNameInColumnsForAuthorization")
                .withReplace("        OA_components: [ site ]",
                        "        OA_components: [  ]")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam("path"));
                    final List<String> expectedComponents = Arrays.stream(new String[]{"site_bassin", "date", "tel_experimental_site", "site", "bassin", "projet", "espece", "ordre_affichage", "chemin", "tel_experimental_network", "plateforme", "is_float_value", "tel_value"})
                            .collect(Collectors.toCollection(LinkedList::new));
                    final Collection<String> givenComponents = (Collection<String>) validationError.getParam("knownComponents");
                    assertIterableEquals(expectedComponents, givenComponents);
                });
    }

    @Test
    public void testMissingArray() {
        CONFIGURATION_INSTANCE.builder("testMissingComponentNameValidation")
                .withReplace("        OA_components: [ site ]",
                        "        OA_components:")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam("path"));
                });
    }

    @Test
    public void testMissingNameApplication() {
        CONFIGURATION_INSTANCE.builder("testMissingNameApplication")
                .withReplace("""
                        OA_name: fake_application""", """
                        OA_name:""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_application > OA_name", validationError.getParam("path"));
                });
    }

    @Test
    public void testMissingOrBadTypeVersionApplication() {
        CONFIGURATION_INSTANCE.builder("testMissingOrBadTypeVersionApplication")
                .withReplace("""
                        OA_version: 3.0.1""", """
                        OA_version: 'deux'""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_VERSION_PATTERN.getMessage(), validationError.getMessage());
                    assertEquals("deux", validationError.getParam("givenVersion"));
                    assertEquals(ConfigurationSchemaNode.OA_APPLICATION, validationError.getParam("path"));
                });
    }

    @Test
    public void testMissingPatternForCheckerDate() {
        CONFIGURATION_INSTANCE.builder("testMissingPatternForCheckerDate")
                .withReplace("""
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern:\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params > OA_pattern", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingRequiredSections() {
        CONFIGURATION_INSTANCE.builder("testMissingRequiredSections")
                .withReplace("""
                        OA_version: 2.0.1""", "")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_VERSION_APPLICATION.getMessage(), validationError.getMessage());
                    assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParams().get("actualVersion"));
                });
    }

    @Test
    public void testMissingRequiredValueForChecker() {
        CONFIGURATION_INSTANCE.builder("testMissingRequiredValueForChecker")
                .withReplace("""
                                          OA_params:
                                            OA_reference:
                                              OA_name: type_de_sites
                                              OA_isParent: true\
                                """,
                        """
                                          OA_params:
                                            OA_reference:
                                              OA_name:
                                              OA_isParent: true\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingRequiredValueForDynamicColumns() {
        CONFIGURATION_INSTANCE.builder("testMissingRequiredValueForDynamicColumns")
                .withReplace(" OA_reference: type_de_sites",
                        " OA_reference:")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_reference", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingMandatorySectionsInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testMissingMandatorySectionsInConstantComponents")
                .withReplace("OA_rowNumber: 1",
                        "")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testMissingReferencesForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testMissingReferencesForAuthorization")
                .withReplace("            OA_reference: projet\n" +
                                "            OA_component: projet",
                        "            OA_component: projet")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals(Set.of(ConfigurationSchemaNode.OA_REFERENCE), validationError.getParam("missingMandatoriesSections"));
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_exportHeader > OA_component > OA_i18n", validationError.getParam(("path")));
                });
    }

    @Test
    public void testMissingRequiredValueForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testMissingRequiredValueForAuthorization")
                .withReplace("            OA_reference: projet\n" +
                                "            OA_component: projet",
                        "            OA_reference:\n" +
                                "            OA_component: projet")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_reference", validationError.getParam(("path")));
                });
    }

    @Test
    public void testNegativeColumnNumberToPreHeaderLineInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testUnknownColumnNumberToFirstRowLineInConstantComponents")
                .withReplace("OA_rowNumber: 1\n" +
                                "          OA_columnNumber: 2",
                        "OA_rowNumber: 1\n" +
                                "          OA_columnNumber: -1")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testNegativeColumnNumberToPostHeaderLineInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testUnknownColumnNumberToFirstRowLineInConstantComponents")
                .withReplace("          OA_rowNumber: 5\n" +
                                "          OA_columnName: \"site\"",
                        "          OA_rowNumber: 5\n" +
                                "          OA_columnNumber: -1")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testNegativeImportHeaderRowNumberInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testNegativeImportHeaderRowNumberInConstantComponents")
                .withReplace("OA_rowNumber: 1",
                        "OA_rowNumber: -1")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_ROW_NUMBER.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_rowNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testNullColumnNumberToFirstRowLineInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testNegativeColumnNumberToFirstRowLineInConstantComponents")
                .withReplace("OA_columnNumber: 2",
                        "OA_columnNumber: 0")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testNotExpectedTagsInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testNotExpectedTagsInConstantComponents")
                .withReplace("      tel_experimental_network:\n" +
                                "        OA_tags: [ test ]",
                        "      tel_experimental_network:\n" +
                                "        OA_tags: [ testz ]")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                    assertEquals(Set.of("testz"), validationError.getParam("notExpectedDomainTags"));
                    assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_tags", validationError.getParam(("path")));
                });
    }

    @Test
    public void testSuperieurImportHeaderRowNumberToFirstRowLineInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testSuperieurImportHeaderRowNumberToFirstRowLineInConstantComponents")
                .withReplace("OA_rowNumber: 1",
                        "OA_rowNumber: 8")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_CONSTANT_IMPORT_HEADER_ROW_NUMBER.getMessage(), validationError.getMessage());
                    assertEquals(8, validationError.getParam("givenRowNumber"));
                    assertEquals(7, validationError.getParam("firstRowLine"));
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_rowNumber", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnExpectedNameTagInBasicComponent() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedNameTagInBasicComponent")
                .withReplace("""
                        OA_tags: [ test, __ORDER_2__ ]""", """
                        OA_tags: [ testz, __ORDER_2__ ]""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_basicComponents > projet > OA_tags", validationError.getParam(("path")));
                    assertEquals(Set.of("testz"), validationError.getParam("notExpectedDomainTags"));
                    assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                });
    }

    @Test
    public void testUnExpectedNameTagInComputedComponents() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedNameTagInComputedComponents")
                .withReplace("      site_bassin:\n" +
                                "        OA_tags: [ __HIDDEN__ ]",
                        "      site_bassin:\n" +
                                "        OA_tags: [ contextt, __HIDDEN__ ]")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_computedComponents > site_bassin > OA_tags", validationError.getParam(("path")));
                    assertEquals(Set.of("contextt"), validationError.getParam("notExpectedDomainTags"));
                    assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                });
    }

    @Test
    public void testUnExpectedNameTagInData() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedNameTagInData")
                .withReplace("""
                        OA_tags: [ context ]""", """
                        OA_tags: [ contxet ]""")
                .test(errors -> {
                    assertEquals(3, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes", validationError.getParam("path"));
                    assertEquals(Set.of("contxet"), validationError.getParam("notExpectedDomainTags"));
                    assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                });
    }

    @Test
    public void testUnExpectedReferencesForComputation() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedReferencesForComputation")
                .withReplace("""
                                          OA_expression: >
                                            return references.sites
                                                    .findAll(){it.refValues.zet_chemin_parent.equals((String)datum.site.bassin)}
                                                    .find{it.refValues.zet_nom_key.equals((String)datum.site.plateforme)}
                                                    .getHierarchicalKey();
                                          OA_references:
                                            - sites\
                                """,
                        """
                                          OA_expression: >
                                            return references.sites
                                                    .findAll(){it.refValues.zet_chemin_parent.equals((String)datum.site.bassin)}
                                                    .find{it.refValues.zet_nom_key.equals((String)datum.site.plateforme)}
                                                    .getHierarchicalKey();
                                          OA_references:
                                            - site\
                                """)
                .test(errors -> {
                    assertEquals(2, errors.size());
                    ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("site", validationError.getParam("referenceName"));
                    assertEquals("OA_data > pem > OA_basicComponents > chemin > OA_defaultValue > OA_references", validationError.getParam(("path")));

                    validationError = errors.get(1);
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    new TreeSet<String>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("site", validationError.getParam("referenceName"));
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_defaultValue > OA_references", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnExpectedReferencesForDefaultValueInBasicComponents() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedReferencesForDefaultValue")
                .withReplace("""
                                          OA_expression: >
                                            return references.sites
                                                    .findAll(){it.refValues.zet_chemin_parent.equals((String)datum.site.bassin)}
                                                    .find{it.refValues.zet_nom_key.equals((String)datum.site.plateforme)}
                                                    .getHierarchicalKey();
                                          OA_references:
                                            - sites\
                                """,
                        """
                                          OA_expression: >
                                            return references.sites
                                                    .findAll(){it.refValues.zet_chemin_parent.equals((String)datum.site.bassin)}
                                                    .find{it.refValues.zet_nom_key.equals((String)datum.site.plateforme)}
                                                    .getHierarchicalKey();
                                          OA_references:
                                            - site\
                                """)
                .test(errors -> {
                    assertEquals(2, errors.size());
                    ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("site", validationError.getParam("referenceName"));
                    assertEquals("OA_data > pem > OA_basicComponents > chemin > OA_defaultValue > OA_references", validationError.getParam(("path")));

                    validationError = errors.get(1);
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("site", validationError.getParam("referenceName"));
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_defaultValue > OA_references", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnExpectedReferencesForDefaultValueInConstantComponents() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedReferencesForDefaultValueInConstantComponents")
                .withReplace("""
                                OA_references:
                                            - sites
                                        OA_exportHeader:""",
                        """
                                OA_references:
                                            - site
                                        OA_exportHeader:""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("site", validationError.getParam("referenceName"));
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_defaultValue > OA_references", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnExpectedReferencesWithoutComponentSectionForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedReferencesForDefaultValue")
                .withReplace("            OA_reference: projet\n" +
                                "            OA_component: projet",
                        "            OA_reference: proj\n" +
                                "            OA_component: projet")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals("projet", validationError.getParam("submissionReference"));
                    assertEquals("proj", validationError.getParam("componentReference"));
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 2", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnExpectedReservedTagPatternForDomainTag() {
        CONFIGURATION_INSTANCE.builder("testUnExpectedReservedTagPatternForDomainTag")
                .withReplace("""
                        context:""", """
                        __HIDDEN__:""")
                .test(errors -> {
                    assertEquals(7, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.ILLEGAL_DOMAIN_TAG_PATTERN.getMessage(), validationError.getMessage());
                    assertEquals(ConfigurationSchemaNode.OA_TAGS, validationError.getParam("path"));
                    assertEquals(Set.of("HiddenTag[tagDefinition=HIDDEN_TAG]"), validationError.getParam("reservedTagNames"));
                    assertEquals("^[a-z][a-z_0-9]*[a-z0-9]$", validationError.getParam("expectedPattern"));
                });
    }

    @Test
    public void testUnExpectedSections() {
        CONFIGURATION_INSTANCE.builder("testUnexpectedSections")
                .withReplace("""
                        OA_version: 2.0.1""", """
                        OA_version: 2.0.1
                        OA_unexpectedTag: 1""")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals(Set.of("OA_unexpectedTag"), validationError.getParams().get("unexpectedSections"));
                });
    }

    @Test
    public void testUnknownCheckerName() {
        CONFIGURATION_INSTANCE.builder("testUnknownCheckerName")
                .withReplace("""
                                      tze_type_nom:
                                        OA_required: true
                                        OA_checker:
                                          OA_name: OA_reference\
                                """,
                        """
                                      tze_type_nom:
                                        OA_required: true
                                        OA_checker:
                                          OA_name: reference\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_CHECKER_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom", validationError.getParam(("path")));
                    final Set<String> expected = Arrays.stream(new String[]{"OA_reference", "OA_boolean", "OA_date", "OA_integer", "OA_float", "OA_string", "OA_groovyExpression"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = (Set<String>) validationError.getParam("acceptedCheckerNames");
                    assertEquals(expected, given);
                    assertEquals("reference", validationError.getParam("checkerName"));
                });
    }

    @Test
    public void testUnknownComponentNameForAuthorization() {
        CONFIGURATION_INSTANCE.builder("testunknownComponentNameForAuthorization")
                .withReplace("OA_component: projet",
                        "OA_component: proj")
                .test(errors -> {
                    assertEquals(2, errors.size());
                    ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 2 > OA_component", validationError.getParam(("path")));
                    assertEquals("proj", validationError.getParam(("unknownComponent")));
                    List<String> expectedComponents = Arrays.stream(new String[]{"site_bassin", "site", "tel_experimental_site", "projet", "espece", "chemin"})
                            .collect(Collectors.toCollection(LinkedList::new));
                    Collection<String> givenComponents = (Collection<String>) validationError.getParam("knownComponents");
                    assertEquals(expectedComponents, givenComponents);

                    validationError = errors.get(1);
                    assertEquals(ConfigurationException.UNKNOWN_NAME_REFERENCE_SCOPE.getMessage(), validationError.getMessage());
                    assertEquals("OA_submission > OA_fileName > OA_referenceScopes > projet", validationError.getParam(("path")));
                    assertEquals("projet", validationError.getParam("unknownAuthorizationScope"));
                    Set<String> expectedReferenceScopes = Arrays.stream(new String[]{"site_bassin", "proj"})
                            .collect(Collectors.toSet());
                    givenComponents = (Collection<String>) validationError.getParam("knownAuthorizationScope");
                    assertEquals(expectedReferenceScopes, givenComponents);
                });
    }

    @Test
    public void testUnknownComponentNameValidation() {
        CONFIGURATION_INSTANCE.builder("testunknownComponentNameValidation")
                .withReplace("        OA_components: [ site ]",
                        "        OA_components: [ sites ]")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam(("path")));
                    assertEquals("sites", validationError.getParam(("unknownComponent")));
                    final List<String> expectedComponents = Arrays.stream(new String[]{"site_bassin", "date", "tel_experimental_site", "site", "bassin", "projet", "espece", "ordre_affichage", "chemin", "tel_experimental_network", "plateforme", "is_float_value", "tel_value"})
                            .collect(Collectors.toCollection(LinkedList::new));
                    final Collection<String> givenComponents = (Collection<String>) validationError.getParam("knownComponents");
                    assertIterableEquals(expectedComponents, givenComponents);
                });
    }

    @Test
    public void testUnknownComponentInTimeScopeInSubmission() {
        CONFIGURATION_INSTANCE.builder("testunknownComponentInTimeScopeInSubmission")
                .withReplace("        OA_timeScope:\n" +
                                "          OA_component: date",
                        "        OA_timeScope:\n" +
                                "          OA_component: dates")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                    assertEquals("dates", validationError.getParam("unknownComponent"));
                    final List<String> expected = Arrays.stream(new String[]{"date"})
                            .collect(Collectors.toCollection(LinkedList::new));
                    final Collection<String> given = (Collection<String>) validationError.getParam("knownComponents");
                    assertIterableEquals(expected, given);
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_timeScope > OA_component", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnknownNameAuthorizationScopeInFileNameInSubmission() {
        CONFIGURATION_INSTANCE.builder("testUnknownNameAuthorizationScopeInFileNameSubmission")
                .withReplace("""
                                        OA_matchPatternScopes:
                                          - projet
                                          - site_bassin\
                                """,
                        """
                                        OA_matchPatternScopes:
                                          - projet
                                          - site_bassine\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_NAME_REFERENCE_SCOPE.getMessage(), validationError.getMessage());
                    assertEquals("site_bassine", validationError.getParam("unknownAuthorizationScope"));
                    final Set<String> expected = Arrays.stream(new String[]{"site_bassin", "projet"})
                            .collect(Collectors.toSet());
                    final Set<String> given = (Set<String>) validationError.getParam("knownAuthorizationScope");
                    assertEquals(expected, given);
                    assertEquals("OA_submission > OA_fileName > OA_referenceScopes > site_bassine", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnknownReferenceColumnToLookForHeaderInDataDynamicComponents() {
        CONFIGURATION_INSTANCE.builder("testUnknownReferenceColumnToLookForHeaderInDataDynamicComponents")
                .withReplace("OA_referenceComponentToLookForHeader: tze_nom_key",
                        "OA_referenceComponentToLookForHeader: nom_key")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_COLUMN_TO_LOOK_FOR_HEADER.getMessage(), validationError.getMessage());
                    assertEquals("type_de_sites", validationError.getParam("referenceName"));
                    assertEquals("nom_key", validationError.getParam("columnNameReference"));
                    final Set<String> expected = Arrays.stream(new String[]{"tze_nom_key", "tze_nom_fr", "tze_nom_en", "tze_definition_fr", "tze_definition_en"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("listColumnsNameReference"));
                    assertEquals(expected, given);
                    assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_referenceComponentToLookForHeader", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnknownReferenceNameForDynamicColumns() {
        CONFIGURATION_INSTANCE.builder("testUnknownReferenceNameForDynamicColumns")
                .withReplace(" OA_reference: type_de_sites",
                        " OA_reference: type_de_site")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_reference", validationError.getParam(("path")));
                    final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("type_de_site", validationError.getParam("referenceName"));
                });
    }

    @Test
    public void testUnknownReferenceNameForChecker() {
        CONFIGURATION_INSTANCE.builder("testUnknownReferenceNameForChecker")
                .withReplace("""
                                          OA_params:
                                            OA_reference:
                                              OA_name: type_de_sites
                                              OA_isParent: true\
                                """,
                        """
                                          OA_params:
                                            OA_reference:
                                              OA_name: tr_type_de_sites
                                              OA_isParent: true\
                                """)
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                    final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                    assertEquals(expected, given);
                    assertEquals("tr_type_de_sites", validationError.getParam("referenceName"));
                });
    }

    @Test
    public void testBadEnumSectionTypeInSubmission() {
        CONFIGURATION_INSTANCE.builder("testBadEnumSectionTypeInSubmission")
                .withReplace("OA_strategy: OA_VERSIONING",
                        "OA_strategy: OA_VERSIONINGY")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.BAD_ENUM_SECTION_TYPE.getMessage(), validationError.getMessage());
                    assertEquals("OA_VERSIONINGY", validationError.getParam("givenValue"));
                    final Set<String> expected = Arrays.stream(new String[]{"OA_INSERTION", "OA_VERSIONING"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = (Set<String>) validationError.getParam("acceptedValues");
                    assertEquals(expected, given);
                    assertEquals("OA_data > pem > OA_submission > OA_strategy", validationError.getParam(("path")));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInTags() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInTags")
                .withReplace("  test:\n" +
                                "    fr: test",
                        "  test:\n" +
                                "    frrr: test")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE.getMessage(), validationError.getMessage());
                    assertEquals("OA_tags > test", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInApplication() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInApplication")
                .withReplace("fr: Application pour de faux",
                        "frrr: Application pour de faux")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_application > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInAuthorizationScopes() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInAuthorizationScopes")
                .withReplace("fr: Projets",
                        "frrr: Projets")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInAuthorizationScopesExportheader() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInAuthorizationScopesExportheader")
                .withReplace("fr: site",
                        "frrr: projet")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 1 > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInDataDynamicComponents() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInDataDynamicComponents")
                .withReplace("fr: Type de Sites",
                        "frrr: Type de Sites")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInDataExportheaderI18n() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInDataExportheaderI18n")
                .withReplace("fr: \"colonne calculée\"",
                        "frrr: \"colonne calculée\"")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes > OA_computedComponents > my_computed_column > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInDataI18n() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInDataI18n")
                .withReplace("fr: Espèces",
                        "frrr: Espèces")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInDataI18ndisplay() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInDataI18ndisplay")
                .withReplace("fr: \"{esp_nom}\"",
                        "frrr: \"{esp_nom}\"")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > especes > OA_i18nDisplayPattern > OA_title > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInDataInConstantComponentsExportheaderI18n() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInDataInConstantComponentsExportheaderI18n")
                .withReplace("fr: \"nom du réseau expérimental\"",
                        "frrr: \"nom du réseau expérimental\"")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_exportHeader > OA_description > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInRightsRequestDescription() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInRightsRequestDescription")
                .withReplace("fr: Vous pouvez demander",
                        "frrr: Vous pouvez demander")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_rightsRequest > OA_i18n > OA_description > en > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testUnsuportedI18nKeyLanguageInValidation() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInValidation")
                .withReplace("fr: les reference",
                        "frrr: les reference")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                    assertEquals("OA_data > pem > OA_validations > reference > OA_i18n > frrr", validationError.getParam("path"));
                });
    }

    @Test
    public void testDuplicatedHeader() {
        CONFIGURATION_INSTANCE.builder("testDuplicatedHeader")
                .withReplace("OA_headerName: \"Nom de la clé du site\"",
                        "OA_headerName: \"zet_chemin_parent\"")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.DUPLICATED_COMPONENT_HEADER.getMessage(), validationError.getMessage());
                    assertIterableEquals(
                            List.of("zet_nom_key", "zet_chemin_parent"),
                            (Iterable<String>) validationError.getParam("duplicatedImportHeader")
                    );
                    assertEquals("OA_data > sites", validationError.getParam("path"));
                    assertEquals("sites", validationError.getParam("data"));
                    assertEquals("zet_chemin_parent", validationError.getParam("duplicatedHeader"));
                });
    }

    @Test
    public void testDuplicatedComponent() {
        CONFIGURATION_INSTANCE.builder("testDuplicatedComponent")
                .withReplace("tel_value",
                        "tel_experimental_site")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.DUPLICATED_COMPONENT_NAME.getMessage(), validationError.getMessage());
                    assertIterableEquals(
                            List.of("OA_data > pem > OA_constantComponents > tel_experimental_site", "OA_data > pem > OA_patternComponents > tel_experimental_site"),
                            (Iterable<String>) validationError.getParam("duplicatedPathes")
                    );
                    assertEquals("OA_data > pem > OA_patternComponents > tel_experimental_site", validationError.getParam("path"));
                });
    }

    @Test
    public void testMissingComponentForDisplayPattern() {
        CONFIGURATION_INSTANCE.builder("testMissingComponentForDisplayPattern")
                .withReplace("    OA_i18nDisplayPattern:\n" +
                             "      OA_title:\n" +
                             "        fr: \"{esp_nom}\"\n" +
                             "        en: \"{esp_nom}\"",
                        "    OA_i18nDisplayPattern:\n" +
                        "      OA_title:\n" +
                        "        fr: \"{esp_invalid_nom}\"\n" +
                        "        en: \"{esp_nom}\"")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.MISSING_COMPONENT_FOR_DISPLAY_PATTERN.getMessage(), validationError.getMessage());

                    final Set<String> expected = Arrays.stream(new String[]{"colonne_homonyme_entre_referentiels", "my_computed_column", "esp_definition_en", "esp_definition_fr", "esp_nom"})
                            .collect(Collectors.toCollection(TreeSet::new));
                    final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("expectedComponent"));
                    assertEquals(expected, given);
                    assertEquals("esp_invalid_nom", validationError.getParam("badGroup"));
                    assertEquals("OA_data > especes > OA_title > fr", validationError.getParam("path"));
                });
    }

    @Test
    public void testduplicatedComponentInPatternComponent() {
        CONFIGURATION_INSTANCE.builder("testduplicatedComponentInPatternComponent")
                .withReplace("swc_qc",
                        "tel_date")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.DUPLICATED_COMPONENT_HEADER_IN_PATTERN_COMPONENT.getMessage(), validationError.getMessage());
                    assertEquals("tel_date", validationError.getParam("qualifierName"));
                    assertEquals("pem", validationError.getParam("data"));
                    assertEquals("tel_value", validationError.getParam("patternComponent"));
                    assertIterableEquals(
                            List.of(
                                    "OA_data > pem > OA_patternComponents > tel_value > OA_componentQualifiers > tel_date",
                                    "OA_data > pem > OA_patternComponents > tel_value > OA_componentAdjacents > tel_date"
                            ),
                            (Iterable<String>) validationError.getParam("duplicatedPathes")
                    );
                    assertEquals("OA_data > pem > OA_patternComponents > tel_value > OA_componentAdjacents > tel_date", validationError.getParam("path"));
                });
    }

    @Test
    public void testduplicatedData() {
        CONFIGURATION_INSTANCE.builder("testUnsuportedI18nKeyLanguageInValidation")
                .withReplace("pem",
                        "sites")
                .test(errors -> {
                    assertEquals(1, errors.size());
                    final ValidationError validationError = errors.getFirst();
                    assertEquals(ConfigurationException.DUPLICATE_KEY.getMessage(), validationError.getMessage());
                });
    }

    @Test
    public void testValidConfiguration() {
        CONFIGURATION_INSTANCE.builder("testValidConfiguration")
                .test(errors -> assertTrue(errors.isEmpty()));
    }


    private class TestConfigurationBuilder {
        static private TestConfigurationBuilder INSTANCE;
        String methodName;
        String path = Fixtures.getValidationApplicationConfigurationResourceName();
        YamlTransformer yamlTransformer = new YamlTransformer("", "");

        public TestConfigurationBuilder() {
            super();
        }

        public TestConfigurationBuilder(final String methodName, final String fileName) {
            super();
            this.methodName = methodName;
            path = "/data/validation/%s".formatted(fileName);
        }

        public TestConfigurationBuilder(final String methodName) {
            super();
            this.methodName = methodName;
        }

        public TestConfigurationBuilder builder(final String methodName) {
            return new TestConfigurationBuilder(methodName);
        }

        public TestConfigurationBuilder builder(final String methodName, final String fileName) {
            return new TestConfigurationBuilder(methodName, fileName);
        }

        public void test(final Consumer<List<ValidationError>> useErrorsPredicate) {
            log.info("Running %s".formatted(methodName));
            try (final InputStream configurationFile = getClass().getResourceAsStream(path)) {
                assert configurationFile != null;
                final String yaml = IOUtils.toString(configurationFile, StandardCharsets.UTF_8);
                final String wrongYaml = yamlTransformer.replace(yaml);
                Exception exception;

                final Object test = buildFluxRequestJDJson(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(new ReactiveProgression.DefaultCounter(0L), fluxSink, new ReactiveProgression.CreateApplicationProgressionMessagesLabel());
                    try {
                        final Application application = ApplicationConfigurationService.parseConfigurationBytes("test", progression, FileBomResolver.of(wrongYaml));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    fluxSink.complete();
                })
                        .flatMap(reactiveResult -> switch (reactiveResult) {
                            case final ReactiveTypeError re -> {
                                errors
                                        .computeIfAbsent(methodName, k -> new LinkedList())
                                        .add(re);
                                yield Flux.just((ValidationError) re.result());
                            }
                            default -> Flux.empty();
                        })
                        .collectList()
                        .map(fe -> {
                            try {
                                useErrorsPredicate.accept(fe);
                                return errors;
                            } catch (final Exception e) {
                                return e;
                            }
                        })
                        .block();
                switch (Objects.requireNonNull(test)) {
                    case final Exception e -> fail(e.getMessage());
                    default -> log.info("test terminé");
                }
            } catch (final IOException e) {
                throw new BadApplicationConfigurationException("impossible de lire le fichier de test", ConfigurationException.IO_EXCEPTION);
            }
        }

        public TestConfigurationBuilder withReplace(final String from, final String by) {
            yamlTransformer = new YamlTransformer(from, by);
            return this;
        }

        record YamlTransformer(String from, String by) {

            YamlTransformer {
                Objects.requireNonNull(from);
                Objects.requireNonNull(by);
            }

            String replace(final String fileContent) {
                if (fileContent == null) {
                    return "";
                }
                return fileContent.replace(from(), by());
            }
        }
    }
}
