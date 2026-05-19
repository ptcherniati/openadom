package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.fixtures.AcbbFixture;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.reactive.*;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import fr.inra.oresing.rest.services.ApplicationConfigurationService;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.inra.oresing.rest.fixtures.MonSoereFixture.getMonsoreApplicationConfigurationResourceName;
import static fr.inra.oresing.rest.fixtures.MonSoereFixture.getMonsoreApplicationConfigurationWithRepositoryResourceName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("SUITE")
@org.junit.jupiter.api.Tag("core.config")
@org.junit.jupiter.api.Tag("GENERATE_CYPRESS_FIXTURES")
public class ApplicationConfigurationServiceTest extends AbstractIntegrationTest {

    public static final Map<String, List<ReactiveResult>> testErrors = new HashMap<>();
    protected TestConfigurationBuilder configurationInstance;

    @AfterAll
    static void registerErrors() throws IOException {
        String baseDirProp = System.getProperty(fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY);
        if (baseDirProp == null) {
            log.debug("registerErrors ignoré (propriété {} non définie)",
                    fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY);
            return;
        }
        final JsonRowMapper jsonMapper = new JsonRowMapper<>();
        final String errorsToJson = jsonMapper.toJson(testErrors);
        fr.inra.oresing.rest.fixtures.CypressFixtureWriter writer =
                new fr.inra.oresing.rest.fixtures.CypressFixtureWriter(java.nio.file.Paths.get(baseDirProp));
        writer.write("ui/cypress/fixtures/applications/errors/errors.json", errorsToJson);
        log.info("register errors file (sanitisé) : {}/ui/cypress/fixtures/applications/errors/errors.json",
                baseDirProp);
    }

    private static Flux<ReactiveResult> buildFluxRequestJDJson(final Consumer<FluxSink<ReactiveResult>> fluxSink) {
        return Flux.create(fluxSink);
    }

    static Stream<TestCase> yamlTestCases() {
        return Stream.of(
                new TestCase(
                        "testBadBuilderVersion",
                        """
                                OA_version: 2.0.1
                                OA_application:
                                """,
                        """
                                OA_version: 2
                                OA_application:
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNSUPPORTED_OPENADOM_VERSION.getMessage(), validationError.getMessage());
                            assertEquals(ConfigurationSchemaNode.OA_VERSION, validationError.getParam("path"));
                            assertEquals("2", validationError.getParam("actualVersion"));
                            assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParam("expectedVersion"));
                        }
                ),
                new TestCase(
                        "testBadDomaineTagPattern",
                        """
                                context:
                                """,
                        """
                                context-:
                                """,
                        errors -> {
                            assertEquals(11, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_DOMAIN_TAG_PATTERN.getMessage(), validationError.getMessage());
                            assertEquals(ConfigurationSchemaNode.OA_TAGS, validationError.getParam("path"));
                            assertEquals(Tag.DomainTag.DOMAIN_PATTERN, validationError.getParam("domainTagPattern"));
                        }
                ),
                new TestCase(
                        "testBadNameApplication",
                        """
                                OA_name: fake_application""", """
                        OA_name: F4KE app!cat°""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNSUPPORTED_NAME_APPLICATION.getMessage(), validationError.getMessage());
                            assertEquals(ConfigurationSchemaNode.OA_APPLICATION, validationError.getParam("path"));
                            assertEquals("F4KE app!cat°", validationError.getParam("nameApplication"));
                        }
                ),
                new TestCase(
                        "testBadNameTag",
                        """
                                OA_tags: [ context ]""", """
                        OA_tags: [ context_ ]""",
                        errors -> {
                            assertEquals(3, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_TAGS_PATTERNS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes", validationError.getParam("path"));
                            assertEquals(Set.of("__HIDDEN__", "__REFERENCE__", "test", "__FILTER_TEXT__", "__FILTER_LIST__", "context", "no-tag", "__ORDER_(\\d*)__", "__DATA__", "__ORDER_STRICT__"), validationError.getParam(("acceptedTagPatterns")));
                        }
                ),
                new TestCase(
                        "tetsManyInNaturalKey",

                        """
                                   esp_nom:
                                """, """
                           esp_nom:
                                OA_checker:
                                  OA_name: OA_string
                                  OA_params:
                                    OA_multiplicity: "MANY"
                        """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MANY_COMPONENT_IN_NATURAL_KEY.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes", validationError.getParam("path"));
                            assertThat((List<String>) validationError.getParam("manyComponents"), containsInAnyOrder("esp_nom"));
                            assertEquals("especes", validationError.getParam(("dataName")));
                        }
                ),
                new TestCase(
                        "testBadNameTagInDynamicComponents",
                        """
                                OA_tags: [ test, context ]""", """
                        OA_tags: [ test_, context ]""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_TAGS_PATTERNS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_tags", validationError.getParam("path"));
                            assertEquals(Set.of("__HIDDEN__", "__REFERENCE__", "test", "__FILTER_TEXT__", "__FILTER_LIST__", "context", "no-tag", "__ORDER_(\\d*)__", "__DATA__", "__ORDER_STRICT__"), validationError.getParam(("acceptedTagPatterns")));
                        }
                ),
                new TestCase(
                        "testMissingReferenceNameForChecker",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                            final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                            assertEquals(expected, given);
                        }
                ),
                new TestCase(
                        "testInvalidDurationForCheckerDate",
                        "          OA_name: OA_date\n" +
                        "          OA_params:",
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_duration: 1 Yearss\
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.INVALID_DURATION_CHECKER_DATE.getMessage(), validationError.getMessage());
                            assertEquals("1 Yearss", validationError.getParam("declaredDuration"));
                            assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testInvalidMinMaxForCheckerDate",
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy
                                            OA_min: 12/31/1980
                                            OA_max: 31/12/2024
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.INVALID_MIN_MAX_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                            assertEquals("12/31/1980", validationError.getParam("declaredMinValue"));
                            assertEquals("31/12/2024", validationError.getParam("declaredMaxValue"));
                            assertEquals("dd/MM/yyyy", validationError.getParam("declaredPattern"));
                            assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testInvalidMinMaxForCheckerDate",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.INVALID_MIN_MAX_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                            assertEquals("31/12/1980", validationError.getParam("declaredMinValue"));
                            assertEquals("12/31/2024", validationError.getParam("declaredMaxValue"));
                            assertEquals("dd/MM/yyyy", validationError.getParam("declaredPattern"));
                            assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testInvalidNaturalKey",
                        """
                                - esp_nom""", """
                        - espNom""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.INVALID_NATURAL_KEY.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes", validationError.getParam(("path")));
                            assertEquals(Set.of("espNom"), validationError.getParam("invalidNaturalKeyElements"));
                            final Set<String> expected = Arrays.stream(new String[]{"esp_nom", "esp_definition_fr", "esp_definition_en", "colonne_homonyme_entre_referentiels", "my_computed_column"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = (Set<String>) validationError.getParam("expectedComponentLabel");
                            assertEquals(expected, given);
                        }
                ),
                new TestCase(
                        "testInvalidPatternForCheckerDate",
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: bb/MM/yyyy\
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.INVALID_PATTERN_FOR_CHECKER_DATE.getMessage(), validationError.getMessage());
                            assertEquals("bb/MM/yyyy", validationError.getParam("badPattern"));
                            assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params > OA_pattern", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingBuilderVersion",
                        """
                                OA_version: 2.0.1
                                OA_application:""", """
                        OA_version:
                        OA_application:""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_VERSION_APPLICATION.getMessage(), validationError.getMessage());
                            assertEquals(ConfigurationSchemaNode.OA_VERSION, validationError.getParam("path"));
                            assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParam("expectedVersion"));
                        }
                ),
                new TestCase(
                        "testMissingNameChecker",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_CHECKER_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom", validationError.getParam(("path")));
                            final Set<String> expected = Arrays.stream(new String[]{"OA_reference", "OA_boolean", "OA_date", "OA_integer", "OA_float", "OA_string", "OA_groovyExpression"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = (Set<String>) validationError.getParam("acceptedCheckerNames");
                            assertEquals(expected, given);
                        }
                ),
                new TestCase(
                        "testMissingAnyMandatorySectionsInConstantComponents",
                        "OA_columnName: \"site\"",
                        "",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_ANY_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                            final Set<String> expectedComponents = Arrays.stream(new String[]{"OA_columnName", "OA_columnNumber"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> givenComponents = (TreeSet<String>) validationError.getParam("anyMandatorySections");
                            assertEquals(expectedComponents, givenComponents);
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_importHeaderTarget > OA_rowNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testmissingRequiredValueInTimeScopeInSubmission",
                        "        OA_timeScope:\n" +
                        "          OA_component: date",
                        "        OA_timeScope:\n" +
                        "          OA_component: ",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_timeScope > OA_component", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingAnyMandatoriesSectionsForAuthorization",
                        "            OA_reference: projet\n" +
                        "            OA_component: projet",
                        "",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                            final Set<String> expectedComponents = Arrays.stream(new String[]{"OA_component", "OA_reference"})
                                    .collect(Collectors.toSet());
                            final Set<String> givenComponents = (Set<String>) validationError.getParam("missingMandatoriesSections");
                            assertEquals(expectedComponents, givenComponents);
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_exportHeader > OA_i18n", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testmissingComponentNameForAuthorization",
                        "            OA_reference: projet\n" +
                        "            OA_component: projet",
                        "            OA_reference: projet\n" +
                        "            OA_component:",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_component", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingComponentNameInColumnsForAuthorization",
                        "        OA_components: [ site ]",
                        "        OA_components: [  ]",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam("path"));
                            final List<String> expectedComponents = Arrays.stream(new String[]{"site_bassin", "date", "tel_experimental_site", "site", "bassin", "projet", "espece", "ordre_affichage", "chemin", "tel_experimental_network", "plateforme", "is_float_value", "tel_value"})
                                    .collect(Collectors.toCollection(LinkedList::new));
                            final Collection<String> givenComponents = (Collection<String>) validationError.getParam("knownComponents");
                            assertIterableEquals(expectedComponents, givenComponents);
                        }
                ),
                new TestCase(
                        "testMissingComponentNameValidation",
                        "        OA_components: [ site ]",
                        "        OA_components:",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testMissingNameApplication",
                        """
                                OA_name: fake_application""", """
                        OA_name:""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_application > OA_name", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testMissingOrBadTypeVersionApplication",
                        """
                                OA_version: 3.0.1""", """
                        OA_version: 'deux'""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_VERSION_PATTERN.getMessage(), validationError.getMessage());
                            assertEquals("deux", validationError.getParam("givenVersion"));
                            assertEquals(ConfigurationSchemaNode.OA_APPLICATION, validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testMissingPatternForCheckerDate",
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern: dd/MM/yyyy\
                                """,
                        """
                                          OA_name: OA_date
                                          OA_params:
                                            OA_pattern:\
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_basicComponents > date > OA_checker > OA_params > OA_pattern", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingRequiredSections",
                        """
                                OA_version: 2.0.1""", "",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_VERSION_APPLICATION.getMessage(), validationError.getMessage());
                            assertEquals(Configuration.OPEN_ADOM_VERSION_PATTERN, validationError.getParams().get("actualVersion"));
                        }
                ),
                new TestCase(
                        "testMissingRequiredValueForChecker",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingRequiredValueForDynamicColumns",
                        " OA_reference: type_de_sites",
                        " OA_reference:",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_reference", validationError.getParam(("path")));

                        }
                ),
                new TestCase(
                        "testMissingMandatorySectionsInConstantComponents",
                        "OA_rowNumber: 1",
                        "",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testMissingReferencesForAuthorization",
                        "            OA_reference: projet\n" +
                        "            OA_component: projet",
                        "            OA_component: projet",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_MANDATORIES_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals(Set.of(ConfigurationSchemaNode.OA_REFERENCE), validationError.getParam("missingMandatoriesSections"));
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_exportHeader > OA_component > OA_i18n", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testMissingRequiredValueForAuthorization",
                        "            OA_reference: projet\n" +
                        "            OA_component: projet",
                        "            OA_reference:\n" +
                        "            OA_component: projet",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_REQUIRED_VALUE.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_reference", validationError.getParam(("path")));

                        }
                ),
                new TestCase(
                        "testUnknownColumnNumberToFirstRowLineInConstantComponents",
                        "OA_rowNumber: 1\n" +
                        "          OA_columnNumber: 2",
                        "OA_rowNumber: 1\n" +
                        "          OA_columnNumber: -1",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnknownColumnNumberToFirstRowLineInConstantComponents",
                        "          OA_rowNumber: 5\n" +
                        "          OA_columnName: \"site\"",
                        "          OA_rowNumber: 5\n" +
                        "          OA_columnNumber: -1",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testNegativeImportHeaderRowNumberInConstantComponents",
                        "OA_rowNumber: 1",
                        "OA_rowNumber: -1",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_ROW_NUMBER.getMessage(), validationError.getMessage());
                        }
                ),
                new TestCase(
                        "testNegativeColumnNumberToFirstRowLineInConstantComponents",
                        "OA_columnNumber: 2",
                        "OA_columnNumber: 0",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_columnNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testNotExpectedTagsInConstantComponents",
                        "      tel_experimental_network:\n" +
                        "        OA_tags: [ test ]",
                        "      tel_experimental_network:\n" +
                        "        OA_tags: [ testz ]",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                            assertEquals(Set.of("testz"), validationError.getParam("notExpectedDomainTags"));
                            assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_tags", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testSuperieurImportHeaderRowNumberToFirstRowLineInConstantComponents",
                        "OA_rowNumber: 1",
                        "OA_rowNumber: 8",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_CONSTANT_IMPORT_HEADER_ROW_NUMBER.getMessage(), validationError.getMessage());
                            assertEquals(8, validationError.getParam("givenRowNumber"));
                            assertEquals(7, validationError.getParam("firstRowLine"));
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_importHeaderTarget > OA_rowNumber", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnexpectedNameTagInBasicComponent",
                        """
                                OA_tags: [ test, __ORDER_2__ ]""", """
                        OA_tags: [ testz, __ORDER_2__ ]""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_basicComponents > projet > OA_tags", validationError.getParam(("path")));
                            assertEquals(Set.of("testz"), validationError.getParam("notExpectedDomainTags"));
                            assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                        }
                ),
                new TestCase(
                        "testUnexpectedNameTagInComputedComponents",
                        "      site_bassin:\n" +
                        "        OA_tags: [ __HIDDEN__ ]",
                        "      site_bassin:\n" +
                        "        OA_tags: [ contextt, __HIDDEN__ ]",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_computedComponents > site_bassin > OA_tags", validationError.getParam(("path")));
                            assertEquals(Set.of("contextt"), validationError.getParam("notExpectedDomainTags"));
                            assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                        }
                ),
                new TestCase(
                        "testUnexpectedNameTagInData",
                        """
                                OA_tags: [ context ]""", """
                        OA_tags: [ contxet ]""",
                        errors -> {
                            assertEquals(3, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.NOT_EXPECTED_DOMAIN_TAGS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes", validationError.getParam("path"));
                            assertEquals(Set.of("contxet"), validationError.getParam("notExpectedDomainTags"));
                            assertEquals(Set.of("test", "context"), validationError.getParam("expectedDomainTags"));
                        }
                ),
                new TestCase(
                        "testUnexpectedReferencesForComputation",
                        """
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
                                """,
                        errors -> {

                        }
                ),
                new TestCase(
                        "testUnexpectedReferencesForDefaultValue",
                        """
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
                                """,
                        errors -> {
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
                        }
                ),
                new TestCase(
                        "testUnexpectedReferencesForDefaultValueInConstantComponents",
                        """
                                OA_references:
                                            - sites
                                        OA_exportHeader:""",
                        """
                                OA_references:
                                            - site
                                        OA_exportHeader:""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                            final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                            assertEquals(expected, given);
                            assertEquals("site", validationError.getParam("referenceName"));
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_site > OA_defaultValue > OA_references", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnexpectedReferencesForDefaultValueInSubmission",
                        "            OA_reference: projet\n" +
                        "            OA_component: projet",
                        "            OA_reference: proj\n" +
                        "            OA_component: projet",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals("projet", validationError.getParam("submissionReference"));
                            assertEquals("proj", validationError.getParam("componentReference"));
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 2", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testUnExpectedReservedTagPatternForDomainTag",
                        """
                                context:""", """
                        __HIDDEN__:""",
                        errors -> {
                            assertEquals(7, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.ILLEGAL_DOMAIN_TAG_PATTERN.getMessage(), validationError.getMessage());
                            assertEquals(ConfigurationSchemaNode.OA_TAGS, validationError.getParam("path"));
                            assertEquals(Set.of("HiddenTag[tagDefinition=HIDDEN_TAG]"), validationError.getParam("reservedTagNames"));
                            assertEquals("^[a-z][a-z_0-9]*[a-z0-9]$", validationError.getParam("expectedPattern"));
                        }
                ),
                new TestCase(
                        "testUnexpectedSections",
                        """
                                OA_version: 2.0.1""", """
                        OA_version: 2.0.1
                        OA_unexpectedTag: 1""",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals(Set.of("OA_unexpectedTag"), validationError.getParams().get("unexpectedSections"));
                        }
                ),
                new TestCase(
                        "testUnknownCheckerName",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_CHECKER_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom", validationError.getParam(("path")));
                            final Set<String> expected = Arrays.stream(new String[]{"OA_reference", "OA_boolean", "OA_date", "OA_integer", "OA_float", "OA_string", "OA_groovyExpression"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = (Set<String>) validationError.getParam("acceptedCheckerNames");
                            assertEquals(expected, given);
                            assertEquals("reference", validationError.getParam("checkerName"));
                        }
                ),
                new TestCase(
                        "testunknownComponentNameForAuthorization",
                        "OA_component: projet",
                        "OA_component: proj",
                        errors -> {
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
                        }
                ),
                new TestCase(
                        "testunknownComponentNameValidation",
                        "        OA_components: [ site ]",
                        "        OA_components: [ sites ]",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_validations > reference > OA_components", validationError.getParam(("path")));
                            assertEquals("sites", validationError.getParam(("unknownComponent")));
                        }
                ),
                new TestCase(
                        "testunknownComponentInTimeScopeInSubmission",
                        "        OA_timeScope:\n" +
                        "          OA_component: date",
                        "        OA_timeScope:\n" +
                        "          OA_component: dates",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME.getMessage(), validationError.getMessage());
                            assertEquals("dates", validationError.getParam("unknownComponent"));
                            final List<String> expected = Arrays.stream(new String[]{"date"})
                                    .collect(Collectors.toCollection(LinkedList::new));
                            final Collection<String> given = (Collection<String>) validationError.getParam("knownComponents");
                            assertIterableEquals(expected, given);
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_timeScope > OA_component", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testUnknownNameAuthorizationScopeInFileNameSubmission",
                        """
                                        OA_matchPatternScopes:
                                          - projet
                                          - site_bassin\
                                """,
                        """
                                        OA_matchPatternScopes:
                                          - projet
                                          - site_bassine\
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_NAME_REFERENCE_SCOPE.getMessage(), validationError.getMessage());
                            assertEquals("site_bassine", validationError.getParam("unknownAuthorizationScope"));
                            final Set<String> expected = Arrays.stream(new String[]{"site_bassin", "projet"})
                                    .collect(Collectors.toSet());
                            final Set<String> given = (Set<String>) validationError.getParam("knownAuthorizationScope");
                            assertEquals(expected, given);
                            assertEquals("OA_submission > OA_fileName > OA_referenceScopes > site_bassine", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testUnknownReferenceColumnToLookForHeaderInDataDynamicComponents",
                        "OA_referenceComponentToLookForHeader: tze_nom_key",
                        "OA_referenceComponentToLookForHeader: nom_key",
                        errors -> {
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
                        }
                ),
                new TestCase(
                        "testUnknownReferenceNameForDynamicColumns",
                        " OA_reference: type_de_sites",
                        " OA_reference: type_de_site",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_reference", validationError.getParam(("path")));
                            final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                            assertEquals(expected, given);
                            assertEquals("type_de_site", validationError.getParam("referenceName"));
                        }
                ),
                new TestCase(
                        "testUnknownReferenceNameForChecker",
                        """
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
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNKNOWN_REFERENCE_NAME.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_basicComponents > tze_type_nom > OA_checker > OA_params > OA_reference > OA_name", validationError.getParam(("path")));
                            final Set<String> expected = Arrays.stream(new String[]{"especes", "type_de_sites", "sites", "pem", "projet"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("allDataNames"));
                            assertEquals(expected, given);
                            assertEquals("tr_type_de_sites", validationError.getParam("referenceName"));
                        }
                ),
                new TestCase(
                        "testBadEnumSectionTypeInSubmission",
                        "OA_strategy: OA_VERSIONING",
                        "OA_strategy: OA_VERSIONINGY",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.BAD_ENUM_SECTION_TYPE.getMessage(), validationError.getMessage());
                            assertEquals("OA_VERSIONINGY", validationError.getParam("givenValue"));
                            final Set<String> expected = Arrays.stream(new String[]{"OA_INSERTION", "OA_VERSIONING"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = (Set<String>) validationError.getParam("acceptedValues");
                            assertEquals(expected, given);
                            assertEquals("OA_data > pem > OA_submission > OA_strategy", validationError.getParam(("path")));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInTags",
                        "  test:\n" +
                        "    fr: test",
                        "  test:\n" +
                        "    frrr: test",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE.getMessage(), validationError.getMessage());
                            assertEquals("OA_tags > test", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInApplication",
                        "fr: Application pour de faux",
                        "frrr: Application pour de faux",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_application > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInAuthorizationScopes",
                        "fr: Projets",
                        "frrr: Projets",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 0 > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInAuthorizationScopesExportheader",
                        "fr: site",
                        "frrr: projet",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_submission > OA_submissionScope > OA_referenceScopes > 1 > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));

                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInDataDynamicComponents",
                        "fr: Type de Sites",
                        "frrr: Type de Sites",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > sites > OA_dynamicComponents > proprieteDeTaxon > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInDataExportheaderI18n",
                        "fr: \"colonne calculée\"",
                        "frrr: \"colonne calculée\"",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes > OA_computedComponents > my_computed_column > OA_exportHeader > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInDataI18n",
                        "fr: Espèces",
                        "frrr: Espèces",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes > OA_i18n > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInDataI18ndisplay",
                        "fr: \"{esp_nom}\"",
                        "frrr: \"{esp_nom}\"",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > especes > OA_i18nDisplayPattern > OA_title > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInDataInConstantComponentsExportheaderI18n",
                        "fr: \"nom du réseau expérimental\"",
                        "frrr: \"nom du réseau expérimental\"",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_data > pem > OA_constantComponents > tel_experimental_network > OA_exportHeader > OA_description > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInRightsRequestDescription",
                        "fr: Vous pouvez demander",
                        "frrr: Vous pouvez demander",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                            assertEquals("OA_rightsRequest > OA_i18n > OA_description > en > frrr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInValidation",
                        "fr: les reference",
                        "frrr: les reference",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.UNEXPECTED_SECTIONS.getMessage(), validationError.getMessage());
                        }
                ),
                new TestCase(
                        "testDuplicatedHeader",
                        "OA_headerName: \"Nom de la clé du site\"",
                        "OA_headerName: \"zet_chemin_parent\"",
                        errors -> {
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
                        }
                ),
                new TestCase(
                        "testDuplicatedComponent",
                        "tel_value",
                        "tel_experimental_site",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.DUPLICATED_COMPONENT_NAME.getMessage(), validationError.getMessage());
                            assertIterableEquals(
                                    List.of("OA_data > pem > OA_constantComponents > tel_experimental_site", "OA_data > pem > OA_patternComponents > tel_experimental_site"),
                                    (Iterable<String>) validationError.getParam("duplicatedPathes")
                            );
                            assertEquals("OA_data > pem > OA_patternComponents > tel_experimental_site", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testMissingComponentForDisplayPattern",
                        """
                                    OA_i18nDisplayPattern:
                                      OA_title:
                                        fr: "{esp_nom}"
                                        en: "{esp_nom}"
                                """,
                        """
                                    OA_i18nDisplayPattern:
                                      OA_title:
                                        fr: "{esp_invalid_nom}"
                                        en: "{esp_nom}"
                                """,
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.MISSING_COMPONENT_FOR_DISPLAY_PATTERN.getMessage(), validationError.getMessage());

                            final Set<String> expected = Arrays.stream(new String[]{"colonne_homonyme_entre_referentiels", "my_computed_column", "esp_definition_en", "esp_definition_fr", "esp_nom"})
                                    .collect(Collectors.toCollection(TreeSet::new));
                            final Set<String> given = new TreeSet<>((Collection<? extends String>) validationError.getParam("expectedComponent"));
                            assertEquals(expected, given);
                            assertEquals("esp_invalid_nom", validationError.getParam("badGroup"));
                            assertEquals("OA_data > especes > OA_title > fr", validationError.getParam("path"));
                        }
                ),
                new TestCase(
                        "testduplicatedComponentInPatternComponent",
                        "swc_qc",
                        "tel_date",
                        errors -> {
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
                        }
                ),
                new TestCase(
                        "testUnsuportedI18nKeyLanguageInValidation",
                        "pem",
                        "sites",
                        errors -> {
                            assertEquals(1, errors.size());
                            final ValidationError validationError = errors.getFirst();
                            assertEquals(ConfigurationException.DUPLICATE_KEY.getMessage(), validationError.getMessage());
                        }
                ),
                new TestCase(
                        "testValidConfiguration",
                        "", "",
                        errors -> assertTrue(errors.isEmpty())
                )
        );
    }

    @TestFactory
    Stream<DynamicTest> yamlValidationTests() {
        return yamlTestCases().map(testCase ->
                DynamicTest.dynamicTest(testCase.testName(), () -> {
                    configurationInstance.builder(testCase.testName())
                            .withReplace(testCase.from(), testCase.to())
                            .test(testCase.assertion());
                })
        );
    }

    @Test
    void multiplesErrors() {
        configurationInstance.builder("testReturnMultiplesErrors")
                .withReplace("  sites:", "  site:")
                .test(errors -> assertTrue(errors.size() > 1));
    }

    @BeforeEach
    public void before() {
        configurationInstance = new TestConfigurationBuilder();
    }

    @Test
    void parseConfigurationFile() {
        List<String> block = Collections.singletonList(buildFluxRequestJDJson(fluxSink -> {
            ImmutableSet<String> configFiles = ImmutableSet.of(
                    AcbbFixture.getAcbbApplicationConfigurationResourceName(),
                    getMonsoreApplicationConfigurationResourceName(),
                    Fixtures.getRecursivityApplicationConfigurationResourceName(),
                    getMonsoreApplicationConfigurationWithRepositoryResourceName(),
                    Fixtures.getPatternApplicationConfigurationResourceName()
                    //Fixtures.getOlaApplicationConfigurationResourceName(),
                    //Fixtures.getHauteFrequenceApplicationConfigurationResourceName(),
                    //Fixtures.getValidationApplicationConfigurationResourceName()
            );
            for (String resourceName : configFiles) {
                parseConfigurationFromResource(resourceName, fluxSink);
            }

            ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");

            // Tests avec différentes configurations
            try {
                testConfiguration(eventHelper, "version: 0");
                testConfiguration(eventHelper, "version: 1");
                testConfiguration(eventHelper, "version: 2");
                testConfiguration(eventHelper, "::");
            } catch (IOException e) {
                throw new OreSiTechnicalException(e.getMessage(), e);
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
            ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");

            Application application = ApplicationConfigurationService.parseConfigurationBytes(
                    "",
                    "test",
                    eventHelper,
                    FileBomResolver.of(in)
            );
            assertNotNull(application, "L'application ne devrait pas être nulle pour " + resource);
            eventHelper.pushResult(application);
        } catch (IOException e) {
            fail("Impossible de lire le fichier de test " + resource + ": " + e.getMessage());
        }
    }

    private void testConfiguration(ReactiveEventHelper eventHelper, String config) throws IOException {
        byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
        FileBomResolver fileBomResolver = FileBomResolver.of(new ByteArrayInputStream(configBytes));
        ApplicationConfigurationService.parseConfigurationBytes("", "",
                eventHelper, fileBomResolver);
    }

    record TestCase(String testName, String from, String to,
                    java.util.function.Consumer<List<ValidationError>> assertion) {
    }

    private class TestConfigurationBuilder {
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

                final Object test = buildFluxRequestJDJson(fluxSink -> {
                    final ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    try {
                        ApplicationConfigurationService.parseConfigurationBytes("", "test", eventHelper, FileBomResolver.of(wrongYaml));
                    } catch (IOException e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    }
                    fluxSink.complete();
                })
                        .flatMap(reactiveResult -> switch (reactiveResult) {
                            case final ReactiveTypeError re -> {
                                testErrors
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
                                return testErrors;
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