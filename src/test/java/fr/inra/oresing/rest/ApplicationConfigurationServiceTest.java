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
import java.util.List;
import java.util.stream.Collectors;

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
    public void parseConfigurationFile() {
        ImmutableSet.of(
                fixtures.getMonsoreApplicationConfigurationResourceName(),
                fixtures.getAcbbApplicationConfigurationResourceName(),
                fixtures.getHauteFrequenceApplicationConfigurationResourceName(),
                fixtures.getValidationApplicationConfigurationResourceName(),
                fixtures.getProApplicationConfigurationResourceName()
        ).forEach(resource -> {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                byte[] bytes = in.readAllBytes();
                ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
                log.debug("résultat de la validation de " + resource + " = " + configurationParsingResult);
                Assert.assertTrue(resource + " doit être reconnu comme un fichier valide",configurationParsingResult.isValid());
            } catch (IOException e) {
                throw new OreSiTechnicalException("ne peut pas lire le fichier de test " + resource, e);
            }
        });

        Assert.assertFalse(service.parseConfigurationBytes("vers: 0".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("version: 1".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("::".getBytes(StandardCharsets.UTF_8)).isValid());
    }

    private ConfigurationParsingResult parseYaml(String toReplace, String by) {
        ConfigurationParsingResult configurationParsingResult;
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getValidationApplicationConfigurationResourceName())) {
            String yaml = IOUtils.toString(configurationFile, StandardCharsets.UTF_8);
            String wrongYaml = yaml.replace(toReplace, by);
            byte[] bytes = wrongYaml.getBytes(StandardCharsets.UTF_8);
            configurationParsingResult = service.parseConfigurationBytes(bytes);
            return configurationParsingResult;
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }
    }

    @Test
    public void testEmptyFile() {
        byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
        ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("emptyFile", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites","");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceForChecker", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceForChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites","refType: sitee");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceForChecker", onlyError.getMessage());
    }

    @Test
    public void testUnsupportedVersion() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("version: 0", "version: -1");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unsupportedVersion", onlyError.getMessage());
    }

    @Test
    public void testUndeclaredDataGroupForVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("data:\n" +
                "            - localization", "data:\n" +
                "            - localizations");
        Assert.assertFalse(configurationParsingResult.isValid());
        long count = configurationParsingResult.getValidationCheckResults()
                .stream()
                .map(ValidationCheckResult::getMessage)
                .filter(mes -> mes.equals("unknownVariablesInDataGroup") || mes.equals("undeclaredDataGroupForVariable"))
                .count();
        Assert.assertEquals(2, count);
    }

    @Test
    public void testVariableInMultipleDataGroup() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("data:\n" +
                "            - Couleur des individus","data:\n" +
                "            - localization\n" +
                "            - Couleur des individus");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("variableInMultipleDataGroup", onlyError.getMessage());
    }

    @Test
    public void testMissingTimeScopeVariableComponentKey() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("component: site\n" +
                "      timeScope:\n" +
                "        variable: date\n" +
                "        component: day","component: site\n");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingTimeScopeVariableComponentKey", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentKeyMissingVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                "        variable: date\n" +
                "        component: day","timeScope:\n" +
                "        component: day");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentKeyMissingVariable", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentKeyUnknownVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                "        variable: date\n" +
                "        component: day","timeScope:\n" +
                "        variable: dates\n" +
                "        component: day");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentKeyUnknownVariable", onlyError.getMessage());
    }

    @Test
    public void testTimeVariableComponentKeyMissingComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                "        variable: date\n" +
                "        component: day","timeScope:\n" +
                "        variable: date\n" +
                "        component: ~");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeVariableComponentKeyMissingComponent", onlyError.getMessage());
    }

    @Test
    public void testTimeVariableComponentKeyUnknownComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                "        variable: date\n" +
                "        component: day","timeScope:\n" +
                "        variable: date\n" +
                "        component: days");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeVariableComponentKeyUnknownComponent", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentWrongChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("checker:\n" +
                "              name: Date", "checker:\n" +
                "              name: Dates");
        Assert.assertFalse(configurationParsingResult.isValid());

        Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "unknownCheckerNameForVariableComponent".equals(e.getMessage()))
                .filter(e -> "site".equals(e.getMessageParams().get("datatype")))
                .filter(e -> "date".equals(e.getMessageParams().get("variable")))
                .filter(e -> "day".equals(e.getMessageParams().get("component")))
                .filter(e -> "Dates".equals(e.getMessageParams().get("checkerName")))
                .collect(Collectors.toList()));
        Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "unknownCheckerNameForVariableComponent".equals(e.getMessage()))
                .filter(e -> "site".equals(e.getMessageParams().get("datatype")))
                .filter(e -> "date".equals(e.getMessageParams().get("variable")))
                .filter(e -> "time".equals(e.getMessageParams().get("component")))
                .filter(e -> "Dates".equals(e.getMessageParams().get("checkerName")))
                .collect(Collectors.toList()));
        Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "timeScopeVariableComponentWrongChecker".equals(e.getMessage()))
                .filter(e -> "Date".equals(e.getMessageParams().get("expectedChecker")))
                .filter(e -> "date".equals(e.getMessageParams().get("variable")))
                .filter(e -> "day".equals(e.getMessageParams().get("component")))
                .collect(Collectors.toList()));
    }

    @Test
    public void testTimeScopeVariableComponentPatternUnknown() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("params:\n" +
                "                pattern: dd/MM/yyyy","params:\n" +
                "                pattern: dd/MM");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentPatternUnknown", onlyError.getMessage());
    }

    @Test
    public void testUnrecognizedProperty() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("compositeReferences","compositReference");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unrecognizedProperty", onlyError.getMessage());
    }

    @Test
    public void testInvalidFormat() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("firstRowLine: 2", "firstRowLine: pas_un_chiffre");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidFormat", onlyError.getMessage());
    }

    @Test
    public void testMissingRequiredExpression() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("\"true\"", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingRequiredExpression", onlyError.getMessage());
    }

    @Test
    public void testIllegalGroovyExpression() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("\"true\"", "if(}");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("illegalGroovyExpression", onlyError.getMessage());
    }

    @Test
    public void testUnknownCheckerName() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("name: GroovyExpression", "name: GroovyExpressions");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownCheckerName", onlyError.getMessage());
    }

    @Test
    public void testCsvBoundToUnknownVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("header: \"typeSite\"\n" +
                "          boundTo:\n" +
                "            variable: localization", "header: \"typeSite\"\n" +
                "          boundTo:\n" +
                "            variable: localizations");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("csvBoundToUnknownVariable", onlyError.getMessage());
    }

    @Test
    public void testCsvBoundToUnknownVariableComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("components:\n" +
                "          site:", "components:\n" +
                "          sites:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("csvBoundToUnknownVariableComponent", onlyError.getMessage());
    }

    @Test
    public void testInvalidKeyColumns() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("keyColumns: [nom du projet_key]", "keyColumns: [nom du projet_clé]");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidKeyColumns", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site", "sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refTypes: sites\n" +
                "            columns: nom du site");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceForCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testUnknownCheckerNameForVariableComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("name: Integer", "name: Integerr");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownCheckerNameForVariableComponent", onlyError.getMessage());
    }

    // ne passe pas car la référence n'est pas bonne nous avons un NullPointerException.
    @Test
    public void testUnknownReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site", "sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: typeSites\n" +
                "            columns: nom du site");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceForCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testMissingColumnReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site", "sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du sites");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingColumnReferenceForCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testUnknownCheckerNameForVariableComponentCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site", "sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: References\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownCheckerNameForVariableComponentCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testMissingParamColumnReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: nom du site", "sitesRef:\n" +
                "        description: \"référence au site\"\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: sites\n" +
                "            columns: ");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingParamColumnReferenceForCheckerInReference", onlyError.getMessage());
    }
}