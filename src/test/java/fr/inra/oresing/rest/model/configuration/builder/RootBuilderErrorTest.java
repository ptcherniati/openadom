package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.ValidationParams;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link RootBuilder} sans Spring / sans Docker.
 *
 * <p>Vérifie que les erreurs sont collectées dans {@code collectedErrors} ET émises via
 * l'EventHelper, et que {@code build()} retourne {@code null} en cas d'erreur.</p>
 */
@Tag("core.config")
@Tag("domain.model")
class RootBuilderErrorTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private record TestSink(List<ReactiveResult> events) implements java.util.function.Consumer<ReactiveResult> {
        @Override
        public void accept(ReactiveResult reactiveResult) {
            events.add(reactiveResult);
        }

        List<ReactiveTypeError> errors() {
            return events.stream()
                    .filter(ReactiveTypeError.class::isInstance)
                    .map(ReactiveTypeError.class::cast)
                    .toList();
        }
    }

    private static ReactiveEventHelper testHelper(TestSink sink) {
        return new ReactiveEventHelper(sink);
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // 1. YAML sans OA_version → retourne null + erreur collectée
    // -----------------------------------------------------------------------

    @Test
    void build_withMissingVersion_returnsNullAndAccumulatesError() {
        String yamlWithoutVersion = """
                OA_application:
                  OA_name: test
                OA_data: {}
                """;

        TestSink sink = new TestSink(new ArrayList<>());
        Configuration result = ConfigurationBuilder.build(
                yaml(yamlWithoutVersion),
                testHelper(sink),
                "test"
        );

        assertNull(result, "build() doit retourner null quand la version est manquante");
        assertFalse(sink.errors().isEmpty(),
                "Au moins une erreur doit avoir été émise via l'EventHelper");
    }

    // -----------------------------------------------------------------------
    // 2. YAML avec mauvaise version → retourne null
    // -----------------------------------------------------------------------

    @Test
    void build_withUnsupportedVersion_returnsNull() {
        String yamlBadVersion = """
                OA_version: 0.0.1
                OA_application:
                  OA_name: test
                OA_data: {}
                """;

        TestSink sink = new TestSink(new ArrayList<>());
        Configuration result = ConfigurationBuilder.build(
                yaml(yamlBadVersion),
                testHelper(sink),
                "test"
        );

        assertNull(result, "build() doit retourner null pour une version non supportée");
        assertFalse(sink.errors().isEmpty(),
                "Une erreur doit être émise pour une version non supportée");
    }

    // -----------------------------------------------------------------------
    // 3. hasErrors() reflète la liste collectedErrors (non le flag mutable)
    // -----------------------------------------------------------------------

    @Test
    void rootBuilder_hasErrors_reflectsCollectedErrors() throws Exception {
        String minimalYaml = """
                OA_application:
                  OA_name: test
                """;

        com.fasterxml.jackson.databind.JsonNode rootNode =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                        .readTree(minimalYaml.getBytes(StandardCharsets.UTF_8));

        TestSink sink = new TestSink(new ArrayList<>());
        RootBuilder rootBuilder = new RootBuilder(testHelper(sink), rootNode, null);

        assertFalse(rootBuilder.hasErrors(),
                "Sans erreur explicitement ajoutée, hasErrors() doit être false");

        rootBuilder.buildError(ConfigurationException.MISSING_VERSION_APPLICATION, "test.path");

        assertTrue(rootBuilder.hasErrors(),
                "Après buildError(), hasErrors() doit être true");
        assertEquals(1, rootBuilder.getCollectedErrors().size(),
                "Exactement une erreur doit être collectée");
        assertEquals(ConfigurationException.MISSING_VERSION_APPLICATION,
                rootBuilder.getCollectedErrors().get(0).exception(),
                "L'exception collectée doit être MISSING_VERSION_APPLICATION");
    }

    // -----------------------------------------------------------------------
    // 4. collectedErrors est une vue immuable (ne fuit pas l'état interne)
    // -----------------------------------------------------------------------

    @Test
    void rootBuilder_collectedErrors_isImmutableView() throws Exception {
        com.fasterxml.jackson.databind.JsonNode rootNode =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                        .readTree("OA_application:\n  OA_name: test\n".getBytes(StandardCharsets.UTF_8));

        TestSink sink = new TestSink(new ArrayList<>());
        RootBuilder rootBuilder = new RootBuilder(testHelper(sink), rootNode, null);
        rootBuilder.buildError(ConfigurationException.MISSING_VERSION_APPLICATION, "path");

        List<ValidationParams> errors = rootBuilder.getCollectedErrors();
        assertThrows(UnsupportedOperationException.class,
                () -> errors.add(null),
                "getCollectedErrors() doit retourner une vue immuable");
    }

    // -----------------------------------------------------------------------
    // 5. Deux erreurs distinctes → toutes les deux collectées
    // -----------------------------------------------------------------------

    @Test
    void rootBuilder_multipleErrors_allCollected() throws Exception {
        com.fasterxml.jackson.databind.JsonNode rootNode =
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                        .readTree("OA_application:\n  OA_name: test\n".getBytes(StandardCharsets.UTF_8));

        TestSink sink = new TestSink(new ArrayList<>());
        RootBuilder rootBuilder = new RootBuilder(testHelper(sink), rootNode, null);

        rootBuilder.buildError(ConfigurationException.MISSING_VERSION_APPLICATION, "path1");
        rootBuilder.buildError(ConfigurationException.UNSUPPORTED_OPENADOM_VERSION, "path2");

        assertEquals(2, rootBuilder.getCollectedErrors().size(),
                "Deux erreurs distinctes doivent toutes deux être collectées");
        assertEquals(2, sink.errors().size(),
                "Les deux erreurs doivent aussi avoir été émises via l'EventHelper");
    }
}