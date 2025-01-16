package fr.inra.oresing.domain.massimport;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.ConfigurationBuiderTestBuilder;
import fr.inra.oresing.domain.application.configuration.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class BuildFileHeaderTest {
    private static final Logger log = LoggerFactory.getLogger(BuildFileHeaderTest.class);
    static final String RESOURCE_PATH = "fr/inra/oresing/domain/massimport/massimport.yaml";

    @ParameterizedTest
    @ValueSource(strings = {RESOURCE_PATH}) // Put your configuration file paths here
    void buildFileHeader(String filePath) throws IOException {
        URL url = Resources.getResource(filePath);
        String config = Resources.toString(url, StandardCharsets.UTF_8);

        // Define your function
        Function<Configuration, Void> myFunction = configuration -> {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            configuration.findData("t_teledetection_tel")
                    .ifPresent(dataDescription-> {
                        try {
                            dataDescription.buildEmptyFile(baos);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            System.out.println("debut");
            System.out.println(baos);
            System.out.println("fin");
            return null;
        };

        // Call the method
        ConfigurationBuiderTestBuilder result = ConfigurationBuiderTestBuilder.of(config, myFunction);

        // Now you can make assertions on the result
        Assertions.assertTrue(result.errors().isEmpty());
    }
}
