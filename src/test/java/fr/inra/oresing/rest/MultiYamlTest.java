package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.data.DataFile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@org.junit.jupiter.api.Tag("core.basic")
public class MultiYamlTest {
    @Test
    void testYaml() throws IOException {
        try (InputStream fileInputStream = getClass().getResourceAsStream("/data/monsore/multiyaml.zip")) {
            // Création du fichier temporaire
            File tempFile = File.createTempFile("multiyaml-", ".zip");
            tempFile.deleteOnExit(); // Nettoyage automatique à la fin du process
            try (OutputStream out = new FileOutputStream(tempFile)) {
                fileInputStream.transferTo(out);
            }
            final DataFile multipartFile = new DataFile(tempFile, 0L, "monzip");
            InputStream bytes = MultiYaml.parseConfigurationBytes(multipartFile);
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