package fr.inra.oresing.rest.model.configuration;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.application.configuration.examples.RootExampleBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

}