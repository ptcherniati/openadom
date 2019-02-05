package fr.inra.oresing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.model.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ConfigurationTest {

    @Test
    public void testReadConfiguration() throws IOException {
        URL resource = getClass().getResource("/data/monsore.yaml");
        try (InputStream in = resource.openStream()){
            byte[] file = in.readAllBytes();
            Configuration conf = Configuration.read(file);

            ObjectMapper json = new ObjectMapper();
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(conf));
            Assert.assertEquals(11, conf.getReferences().size());
        }
    }
}
