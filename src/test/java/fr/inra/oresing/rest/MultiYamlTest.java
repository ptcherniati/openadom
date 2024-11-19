package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@org.junit.jupiter.api.Tag("SUITE")
public class MultiYamlTest {
    @Test
    public void testYaml() throws IOException {
        try (InputStream fileInputStream = getClass().getResourceAsStream("/data/foret/multiyaml/multiyaml.zip")) {
            final MultipartFile multipartFile = new MockMultipartFile("monzip", fileInputStream);
            byte[] bytes = new MultiYaml().parseConfigurationBytes(multipartFile).readAllBytes();
            Object configuration = new YAMLMapper().readValue(bytes, Object.class);
            assertNotNull(configuration);
            assertNotNull(((Map) configuration).get("dataTypes"));
            assertEquals(3, ((Map) ((Map) configuration).get("dataTypes")).size());
            assertNotNull(((Map) configuration).get("references"));
            assertEquals(2, ((Map) ((Map) configuration).get("references")).size());
            assertNotNull(((Map) configuration).get("application"));
            assertEquals("foret", ((Map) ((Map) configuration).get("application")).get("name"));
            assertEquals(1, ((Map) ((Map) configuration).get("application")).get("version"));
            assertNotNull(((Map) configuration).get("compositeReferences"));
            assertEquals(1, ((Map) ((Map) configuration).get("compositeReferences")).size());
            assertNotNull(((Map) configuration).get("compositeReferences"));
            assertEquals(1, ((Map) ((Map) configuration).get("compositeReferences")).size());
        }
    }
}