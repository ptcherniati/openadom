package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.examples.RootExampleBuilder;
import fr.inra.oresing.rest.model.configuration.builder.RootBuilder;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@org.junit.jupiter.api.Tag("SUITE")
class ConfigurationSchemaNodeTest {
    @Test
    void buildExample() throws IOException {
        final URL url = Resources.getResource("data/configuration/schemaExample.yaml");
        final String expectedSchema = Resources.toString(url, StandardCharsets.UTF_8)
                .replace("   \" \"   ", " + \" \" + ");
        final String exampleOfFile = RootExampleBuilder.buildRootSchema().buildExample(0);
        Assertions.assertEquals(expectedSchema, exampleOfFile);
    }

    @Test
    void testExampleConfigurationIsReadable() throws IOException {
        final String exampleOfFile = RootExampleBuilder.buildRootSchema().buildExample(0);
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        com.fasterxml.jackson.databind.JsonNode rootNode = yamlMapper.readTree(exampleOfFile);

        ReactiveEventHelper eventHelper = Mockito.mock(ReactiveEventHelper.class);
        DocumentContext documentContext = Mockito.mock(DocumentContext.class);

        RootBuilder rootBuilder = new RootBuilder(eventHelper, rootNode, documentContext);
        Configuration configuration = rootBuilder.build(new ByteArrayInputStream(exampleOfFile.getBytes(StandardCharsets.UTF_8)), "test comment");

        Assertions.assertNotNull(configuration);
        Mockito.verify(eventHelper, Mockito.never()).pushError(Mockito.any(), Mockito.any());
    }
}