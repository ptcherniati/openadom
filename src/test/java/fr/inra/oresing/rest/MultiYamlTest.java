package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class MultiYamlTest {
    @Test
    public void testYaml() throws IOException {
        try (final InputStream fileInputStream = getClass().getResourceAsStream("/data/foret/multiyaml/multiyaml.zip")) {
            MultipartFile multipartFile = new MockMultipartFile("monzip", fileInputStream);
            final byte[] bytes = new MultiYaml().parseConfigurationBytes(multipartFile);
            final Object configuration = new YAMLMapper().readValue(bytes, Object.class);
            Assert.assertNotNull(configuration);
            Assert.assertNotNull(((Map) configuration).get("dataTypes"));
            Assert.assertEquals(3, ((Map) ((Map) configuration).get("dataTypes")).size());
            Assert.assertNotNull(((Map) configuration).get("references"));
            Assert.assertEquals(2, ((Map) ((Map) configuration).get("references")).size());
            Assert.assertNotNull(((Map) configuration).get("application"));
            Assert.assertEquals("foret", ((Map) ((Map) configuration).get("application")).get("name"));
            Assert.assertEquals(1, ((Map) ((Map) configuration).get("application")).get("version"));
            Assert.assertNotNull(((Map) configuration).get("compositeReferences"));
            Assert.assertEquals(1, ((Map) ((Map) configuration).get("compositeReferences")).size());
            Assert.assertNotNull(((Map) configuration).get("compositeReferences"));
            Assert.assertEquals(1, ((Map) ((Map) configuration).get("compositeReferences")).size());
        }
    }
}