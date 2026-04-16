package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.apache.commons.collections4.CollectionUtils;
import org.assertj.core.api.Assertions;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("SUITE")
class ConfigurationBuilderTest {

    public static InputStream CONFIGURATION;
    public static InputStream SCHEMA;
    public static InputStream MONSORE_CONFIGURATION;
    public static InputStream LOCALIZATION_RESULT;
    public static InputStream DATA_RESULT;
    public static InputStream LOCALIZATION_MONSORE_RESULT;
    public static InputStream DATA_MONSORE_RESULT;
    public static InputStream LOCALIZATION_EXAMPLE_RESULT;
    public static InputStream DATA_EXAMPLE_RESULT;
    public static InputStream HIERARCHICAL_CONFIGURATION;
    public static InputStream HIERARCHICAL_RESULT;
    private List<ValidationError> errors;
    private Configuration configuration;

    @BeforeAll
    static void getConfigurationFile() throws IOException {
        URL url = Resources.getResource("data/configuration/configuration.yaml");
        CONFIGURATION = url.openStream();
        url = Resources.getResource("data/configuration/schemaExample.yaml");
        SCHEMA = url.openStream();
        url = Resources.getResource("data/monsore/monsore-with-repository.yaml");
        MONSORE_CONFIGURATION = url.openStream();
        url = Resources.getResource("data/configuration/hierarchical.yaml");
        HIERARCHICAL_CONFIGURATION = url.openStream();
        url = Resources.getResource("data/configuration/localization.result.json");
        LOCALIZATION_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/data.result.json");
        DATA_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/localization.monsore.result.json");
        LOCALIZATION_MONSORE_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/data.result.monsore.json");
        DATA_MONSORE_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/localization.example.result.json");
        LOCALIZATION_EXAMPLE_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/data.result.example.json");
        DATA_EXAMPLE_RESULT = url.openStream();
        url = Resources.getResource("data/configuration/hierarchical.json");
        HIERARCHICAL_RESULT = url.openStream();
    }

    private static void testConfiguration(final Configuration configuration) throws IOException {
        testTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testInternationalisation(configuration.i18n());
        testApplicationDescription(configuration.applicationDescription());
        testComponents(configuration.dataDescription());
    }

    private static void testExampleConfiguration(final Configuration configuration) throws IOException {
        testExampleTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testExampleInternationalisation(configuration.i18n());
        testExampleApplicationDescription(configuration.applicationDescription());
        testExampleComponents(configuration.dataDescription());
    }

    private static void testMonsoreConfiguration(final Configuration configuration) throws IOException, JSONException {
        testMonsoreTags(configuration.tags());
        assertEquals("2.0.1", configuration.version().version());
        testMonsoreInternationalisation(configuration.i18n());
        testMonsoreApplicationDescription(configuration.applicationDescription());
        testMonsoreComponents(configuration.dataDescription());
    }

    private static void testComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String actualJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(dataDescriptionMap);
        JsonAssertions.assertThatJson(actualJson)
                        .isEqualTo(new String(DATA_RESULT.readAllBytes(), StandardCharsets.UTF_8));
    }


    private static void testMonsoreComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String actualJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(dataDescriptionMap);
        JsonAssertions.assertThatJson(actualJson)
                        .isEqualTo(new String(DATA_MONSORE_RESULT.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void testExampleComponents(final Map<String, StandardDataDescription> dataDescriptionMap) throws IOException {

        String actualJson = new ObjectMapper().registerModule(new JavaTimeModule())
                .writer()
                .withDefaultPrettyPrinter()
                .writeValueAsString(dataDescriptionMap);
        JsonAssertions.assertThatJson(actualJson)
                        .isEqualTo(new String(DATA_EXAMPLE_RESULT.readAllBytes(), StandardCharsets.UTF_8));

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

    private static void testInternationalisation(final Internationalizations localizations) throws IOException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(new String(LOCALIZATION_RESULT.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void testMonsoreInternationalisation(final Internationalizations localizations) throws IOException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(new String(LOCALIZATION_MONSORE_RESULT.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void testExampleInternationalisation(final Internationalizations localizations) throws IOException {
        Assertions.assertThat(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(localizations))
                .isEqualTo(new String(LOCALIZATION_EXAMPLE_RESULT.readAllBytes(), StandardCharsets.UTF_8));
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
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    configuration = ConfigurationBuilder.build(CONFIGURATION, eventHelper, "une application de test");
                    try {
                        testConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    } catch (IOException e) {
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
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    configuration = ConfigurationBuilder.build(SCHEMA, eventHelper, "une application de test");
                    try {
                        testExampleConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    } catch (IOException e) {
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
    void buildHierarchicalTest() {
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    configuration = ConfigurationBuilder.build(HIERARCHICAL_CONFIGURATION, eventHelper, "un commentaire");
                    assertNotNull(configuration);
                    try {
                        testHierarchicalNodes(configuration.hierarchicalNodes());
                        fluxSink.complete();
                    } catch (final JsonProcessingException e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    } catch (IOException e) {
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

    private void testHierarchicalNodes(SortedSet<Node> nodes) throws IOException {
        Assertions.assertThat(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(nodes))
                .isEqualTo(new String(HIERARCHICAL_RESULT.readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void buildMonsoreTest() {
        errors = Flux.<ReactiveResult>create(fluxSink -> {
                    ReactiveEventHelper eventHelper = new ReactiveEventHelper(fluxSink::next, "test");
                    configuration = ConfigurationBuilder.build(MONSORE_CONFIGURATION, eventHelper, "un commentaire");
                    try {
                        testMonsoreConfiguration(Objects.requireNonNull(configuration));
                        fluxSink.complete();
                    } catch (final JsonProcessingException | JSONException e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    } catch (IOException e) {
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