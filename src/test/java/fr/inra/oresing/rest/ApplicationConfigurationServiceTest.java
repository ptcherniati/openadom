package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class ApplicationConfigurationServiceTest {

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ApplicationConfigurationService service;

    @Test
    public void parseConfigurationFile() throws IOException {
        ImmutableSet.of(
                fixtures.getMonsoreApplicationConfigurationResourceName(),
                fixtures.getAcbbApplicationConfigurationResourceName()
        ).forEach(resource -> {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                byte[] bytes = in.readAllBytes();
                ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
                Assert.assertTrue(resource + " doit être reconnu comme un fichier valide",configurationParsingResult.isValid());
            } catch (IOException e) {
                throw new OreSiTechnicalException("ne peut pas lire le fichier de test " + resource, e);
            }
        });

        Assert.assertFalse(service.parseConfigurationBytes("".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("vers: 0".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("version: 1".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("::".getBytes(StandardCharsets.UTF_8)).isValid());

        try (InputStream in = getClass().getResourceAsStream(fixtures.getMonsoreApplicationConfigurationResourceName())) {
            String yaml = IOUtils.toString(in, StandardCharsets.UTF_8);
            String wrongYaml = yaml.replace("firstRowLine: 5", "firstRowLine: pas_un_chiffre");
            byte[] bytes = wrongYaml.getBytes(StandardCharsets.UTF_8);
            ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
            System.out.println(configurationParsingResult);
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessageParams().containsValue("pas_un_chiffre"));
        }
    }
}