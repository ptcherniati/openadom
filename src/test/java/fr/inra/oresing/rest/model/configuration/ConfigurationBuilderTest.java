package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import org.apache.commons.collections4.CollectionUtils;
import org.assertj.core.api.Assertions;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("SUITE")
class ConfigurationBuilderTest {

    public static String CONFIGURATION;
    public static String SCHEMA;
    public static String MONSORE_CONFIGURATION;
    public static String LOCALIZATION_RESULT;
    public static String DATA_RESULT;
    public static String LOCALIZATION_MONSORE_RESULT;
    public static String DATA_MONSORE_RESULT;
    public static String LOCALIZATION_EXAMPLE_RESULT;
    public static String DATA_EXAMPLE_RESULT;
    public static String HIERARCHICAL_CONFIGURATION;
    public static String HIERARCHICAL_RESULT;
    private List<ValidationError> errors;
    private Configuration configuration;

    @BeforeAll
    static void getConfigurationFile() throws IOException {
        URL url = Resources.getResource("data/configuration/configuration.yaml");
        CONFIGURATION = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/schemaExample.yaml");
        SCHEMA = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/monsore/monsore-with-repository.yaml");
        MONSORE_CONFIGURATION = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/hierarchical.yaml");
        HIERARCHICAL_CONFIGURATION = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/localization.result.json");
        LOCALIZATION_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/data.result.json");
        DATA_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/localization.monsore.result.json");
        LOCALIZATION_MONSORE_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/data.result.monsore.json");
        DATA_MONSORE_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/localization.example.result.json");
        LOCALIZATION_EXAMPLE_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/data.result.example.json");
        DATA_EXAMPLE_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
        url = Resources.getResource("data/configuration/hierarchical.json");
        HIERARCHICAL_RESULT = Resources.toString(url, StandardCharsets.UTF_8);
    }

    private static void testConfiguration(final Configuration configuration) throws JsonProcessingException, JSONException {
        testTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testInternationalisation(configuration.i18n());
        testApplicationDescription(configuration.applicationDescription());
        testComponents(configuration.dataDescription());
    }

    private static void testExampleConfiguration(final Configuration configuration) throws JsonProcessingException, JSONException {
        testExampleTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testExampleInternationalisation(configuration.i18n());
        testExampleApplicationDescription(configuration.applicationDescription());
        testExampleComponents(configuration.dataDescription());
    }

    private static void testMonsoreConfiguration(final Configuration configuration) throws JsonProcessingException, JSONException {
        testMonsoreTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testMonsoreInternationalisation(configuration.i18n());
        testMonsoreApplicationDescription(configuration.applicationDescription());
        testMonsoreComponents(configuration.dataDescription());
    }

    private static void testComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws JsonProcessingException, JSONException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String actualJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dataDescriptionMap);

        // Compare les deux JSON sans tenir compte de l'ordre des champs (mode LENIENT)
        JSONAssert.assertEquals(DATA_RESULT, actualJson, JSONCompareMode.LENIENT);
    }

    private static void testMonsoreComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws JsonProcessingException, JSONException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String actualJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dataDescriptionMap);

        // Compare les JSON sans tenir compte de l'ordre des champs
        JSONAssert.assertEquals(DATA_MONSORE_RESULT, actualJson, JSONCompareMode.LENIENT);
    }

    private static void testExampleComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws JsonProcessingException, JSONException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String actualJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dataDescriptionMap);

        // Compare les JSON sans tenir compte de l'ordre des champs
        JSONAssert.assertEquals(DATA_EXAMPLE_RESULT, actualJson, JSONCompareMode.LENIENT);
    }

    private static void testApplicationDescription(final ApplicationDescription applicationDescription) {
        Assertions.assertThat(applicationDescription.name())
                .isEqualTo("monapplication");
        Assertions.assertThat(applicationDescription.version().version())
                .isEqualTo("4.0.1");
        Assertions.assertThat(applicationDescription.defaultLanguage())
                .isEqualTo(Locale.ENGLISH);
        Assertions.assertThat(applicationDescription.comment())
                .isEqualTo("une application de test");

    }

    private static void testMonsoreApplicationDescription(final ApplicationDescription applicationDescription) {
        Assertions.assertThat(applicationDescription.name())
                .isEqualTo("monsore");
        Assertions.assertThat(applicationDescription.version().version())
                .isEqualTo("3.0.1");
        Assertions.assertThat(applicationDescription.defaultLanguage())
                .isEqualTo(Locale.FRENCH);
        Assertions.assertThat(applicationDescription.comment())
                .isEqualTo("un commentaire");

    }

    private static void testExampleApplicationDescription(final ApplicationDescription applicationDescription) {
        Assertions.assertThat(applicationDescription.name())
                .isEqualTo("monsore");
        Assertions.assertThat(applicationDescription.version().version())
                .isEqualTo("3.0.1");
        Assertions.assertThat(applicationDescription.defaultLanguage())
                .isEqualTo(Locale.FRENCH);
        Assertions.assertThat(applicationDescription.comment())
                .isEqualTo("une application de test");

    }

    private static void testInternationalisation(final Internationalizations localizations) throws JsonProcessingException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(LOCALIZATION_RESULT);
    }

    private static void testMonsoreInternationalisation(final Internationalizations localizations) throws JsonProcessingException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(LOCALIZATION_MONSORE_RESULT);
    }

    private static JSONObject toJsonObject(Object json) {
        return new JsonRowMapper<>().convertValue(json, JSONObject.class);
    }

    private static void testExampleInternationalisation(final Internationalizations localizations) throws JsonProcessingException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(LOCALIZATION_EXAMPLE_RESULT);
    }

    private static void testTags(final Set<Tag> tags) {
        Assertions.assertThat(tags)
                .contains(new Tag.DomainTag("temporal"),
                        new Tag.DomainTag("context"),
                        new Tag.DomainTag("data"),
                        new Tag.DomainTag("test"),
                        new Tag.DomainTag("unit"))
                .hasSize(5);
    }

    private static void testMonsoreTags(final Set<Tag> tags) {
        Assertions.assertThat(tags)
                .contains(new Tag.DomainTag("context"),
                        new Tag.DomainTag("data"),
                        new Tag.DomainTag("test"),
                        new Tag.DomainTag("unit"),
                        new Tag.DomainTag("temporal"))
                .hasSize(5);
    }

    private static void testExampleTags(final Set<Tag> tags) {
        Assertions.assertThat(tags)
                .contains(new Tag.DomainTag("context"),
                        new Tag.DomainTag("data"))
                .hasSize(2);
    }

    @Test
    void buildApplicationTest() {
        final YAMLMapper yamlMapper = YAMLMapper.builder().build();
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
                    configuration = ConfigurationBuilder.build(CONFIGURATION.getBytes(), progression, "une application de test");
                    try {
                        testConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(reactiveResult -> switch (reactiveResult) {
                    case final ReactiveTypeError re -> Mono.just(re);
                    default -> Mono.empty();
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        assertTrue(CollectionUtils.isEmpty(errors), errors.stream()
                .map(ValidationError::getValidationErrorString)
                .toList()
                .toString());

    }

    @Test
    void buildApplicationSchemaTest() {
        final YAMLMapper yamlMapper = YAMLMapper.builder().build();
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
                    configuration = ConfigurationBuilder.build(SCHEMA.getBytes(), progression, "une application de test");
                    try {
                        testExampleConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(reactiveResult -> switch (reactiveResult) {
                    case final ReactiveTypeError re -> Mono.just(re);
                    default -> Mono.empty();
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        assertTrue(CollectionUtils.isEmpty(errors), errors.stream()
                .map(ValidationError::getValidationErrorString)
                .toList()
                .toString());
    }

    private boolean throwErrors(final List<ValidationError> errors) {
        this.errors = errors;
        return false;
    }

    @Test
    void buildHierarchicalTest() {
        final YAMLMapper yamlMapper = YAMLMapper.builder().build();
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
                    configuration = ConfigurationBuilder.build(HIERARCHICAL_CONFIGURATION.getBytes(), progression, "un commentaire");
                    assertNotNull(configuration);
                    try {
                        testHierarchicalNodes(configuration.hierarchicalNodes());
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(reactiveResult -> switch (reactiveResult) {
                    case final ReactiveTypeError re -> Mono.just(re);
                    default -> Mono.empty();
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        assertTrue(CollectionUtils.isEmpty(errors), errors.stream()
                .map(ValidationError::getValidationErrorString)
                .toList()
                .toString());
    }

    private void testHierarchicalNodes(SortedSet<Node> nodes) throws JsonProcessingException {
        Assertions.assertThat(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes))
                .isEqualTo(HIERARCHICAL_RESULT);
    }

    @Test
    void buildMonsoreTest() {
        final YAMLMapper yamlMapper = YAMLMapper.builder().build();
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
                    configuration = ConfigurationBuilder.build(MONSORE_CONFIGURATION.getBytes(), progression, "un commentaire");
                    try {
                        testMonsoreConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException | JSONException e) {
                        throw new RuntimeException(e);
                    }
                })
                .flatMap(reactiveResult -> switch (reactiveResult) {
                    case final ReactiveTypeError re -> Mono.just(re);
                    default -> Mono.empty();
                })
                .map(ReactiveTypeError::result)
                .map(ValidationError.class::cast)
                .collectList()
                .block();
        assertTrue(CollectionUtils.isEmpty(errors), errors.stream()
                .map(ValidationError::getValidationErrorString)
                .toList()
                .toString());
    }
}