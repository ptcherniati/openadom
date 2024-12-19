package fr.inra.oresing.domain.massimport;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.ConfigurationBuiderTestBuilder;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildFileHeadertest {
    private static final Logger log = LoggerFactory.getLogger(BuildFileHeadertest.class);
    static final String RESOURCE_PATH = "fr/inra/oresing/domain/massimport/massimport.yaml";

    @ParameterizedTest
    @Disabled
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
            System.out.println(baos.toString());
            System.out.println("fin");
            return null;
        };

        // Call the method
        ConfigurationBuiderTestBuilder result = ConfigurationBuiderTestBuilder.of(config, myFunction);

        // Now you can make assertions on the result
        Assertions.assertTrue(result.errors().isEmpty());
    }
}
