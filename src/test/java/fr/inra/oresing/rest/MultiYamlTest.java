package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import static org.junit.jupiter.api.Assertions.*;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@org.junit.jupiter.api.Tag("core.basic")
public class MultiYamlTest {
    @Test
    public void testYaml() throws IOException {
        try (InputStream fileInputStream = getClass().getResourceAsStream("/data/monsore/multiyaml.zip")) {
            final MultipartFile multipartFile = new MockMultipartFile("monzip", fileInputStream);
            byte[] bytes = MultiYaml.parseConfigurationBytes(multipartFile).readAllBytes();
            Object configuration = new YAMLMapper().readValue(bytes, Object.class);
            assertNotNull(configuration);
            assertNotNull(((Map) configuration).get(ConfigurationSchemaNode.OA_DATA));
            assertEquals(11, ((Map) ((Map) configuration).get(ConfigurationSchemaNode.OA_DATA)).size());
            assertNotNull(((Map) configuration).get(ConfigurationSchemaNode.OA_VERSION));
            assertNotNull(((Map) configuration).get(ConfigurationSchemaNode.OA_APPLICATION));
            assertEquals(5, ((Map) ((Map) configuration).get(ConfigurationSchemaNode.OA_APPLICATION)).size());
            assertNotNull(((Map) configuration).get(ConfigurationSchemaNode.OA_TAGS));
            assertEquals(5, ((Map) ((Map) configuration).get(ConfigurationSchemaNode.OA_TAGS)).size());
        }
    }
}