package fr.inra.oresing.domain.massimport;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.ConfigurationBuiderTestBuilder;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

@Tag("domain.model")
class BuildFileHeaderTest {
    static final String RESOURCE_PATH = "fr/inra/oresing/domain/massimport/massimport.yaml";

    @ParameterizedTest
    @ValueSource(strings = {RESOURCE_PATH})
        // Put your configuration file paths here
    void buildFileHeader(String filePath) throws IOException {
        URL url = Resources.getResource(filePath);
        InputStream config = url.openStream();

        // Define your function
        Function<Configuration, Void> myFunction = configuration -> {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            configuration.findData("t_teledetection_tel")
                    .ifPresent(dataDescription -> {
                        try {
                            dataDescription.buildEmptyFile(baos);
                        } catch (IOException e) {
                            throw new OreSiTechnicalException(e.getMessage(), e);
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