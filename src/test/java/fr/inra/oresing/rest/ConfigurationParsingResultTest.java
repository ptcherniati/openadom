package fr.inra.oresing.rest;

import com.google.common.collect.Iterables;
import fr.inra.oresing.OreSiNg;
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
public class ConfigurationParsingResultTest {

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ApplicationConfigurationService service;

    private ConfigurationParsingResult parseYaml(String toReplace, String by)throws Exception{
        ConfigurationParsingResult configurationParsingResult;
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            String yaml = IOUtils.toString(configurationFile, StandardCharsets.UTF_8);
            String wrongYaml = yaml.replace(toReplace, by);
            byte[] bytes = wrongYaml.getBytes(StandardCharsets.UTF_8);
            configurationParsingResult = service.parseConfigurationBytes(bytes);
            return configurationParsingResult;
        }
    }

    @Test
    public void testEmptyFile() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            String yaml = IOUtils.toString(configurationFile, StandardCharsets.UTF_8);
            String wrongYaml = yaml.replace(yaml, "");
            byte[] bytes = wrongYaml.getBytes(StandardCharsets.UTF_8);
            ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "emptyFile");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testMissingReferenceForChecker() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites","");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "missingReferenceForChecker");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testUnknownReferenceForChecker() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites","refType: sitee");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "unknownReferenceForChecker");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testUnsupportedVersion() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("version: 0", "version: -1");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "unsupportedVersion");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testUndeclaredDataGroupForVariable() throws Exception {
        ConfigurationParsingResult configurationParsingResult = parseYaml("data:\n" +
                "            - localization", "data:\n" +
                "            - localizations");
        Assert.assertFalse(configurationParsingResult.isValid());
        long count = configurationParsingResult.getValidationCheckResults()
                .stream()
                .map(ValidationCheckResult::getMessage)
                .filter(mes -> mes.equals("unknownVariablesInDataGroup") || mes.equals("undeclaredDataGroupForVariable"))
                .count();
        Assert.assertTrue(count==2);
    }

    @Test
    public void testVariableInMultipleDataGroup() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("data:\n" +
                    "            - Couleur des individus","data:\n" +
                    "            - localization\n" +
                    "            - Couleur des individus");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "variableInMultipleDataGroup");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testMissingTimeScopeVariableComponentKey() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("component: site\n" +
                    "      timeScope:\n" +
                    "        variable: date\n" +
                    "        component: day","component: site\n");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "missingTimeScopeVariableComponentKey");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeScopeVariableComponentKeyMissingVariable() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                    "        variable: date\n" +
                    "        component: day","timeScope:\n" +
                    "        component: day");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeScopeVariableComponentKeyMissingVariable");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeScopeVariableComponentKeyUnknownVariable() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                    "        variable: date\n" +
                    "        component: day","timeScope:\n" +
                    "        variable: dates\n" +
                    "        component: day");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeScopeVariableComponentKeyUnknownVariable");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeVariableComponentKeyMissingComponent() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                    "        variable: date\n" +
                    "        component: day","timeScope:\n" +
                    "        variable: date\n" +
                    "        component: ~");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeVariableComponentKeyMissingComponent");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeVariableComponentKeyUnknownComponent() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
                    "        variable: date\n" +
                    "        component: day","timeScope:\n" +
                    "        variable: date\n" +
                    "        component: days");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeVariableComponentKeyUnknownComponent");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeScopeVariableComponentWrongChecker() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("checker:\n" +
                    "              name: Date", "checker:\n" +
                    "              name: Dates");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeScopeVariableComponentWrongChecker");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testTimeScopeVariableComponentPatternUnknown() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("params:\n" +
                    "                pattern: dd/MM/yyyy","params:\n" +
                    "                pattern: dd/MM");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "timeScopeVariableComponentPatternUnknown");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testUnrecognizedProperty() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("compositeReferences","compositReference");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "unrecognizedProperty");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testInvalidFormat() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("firstRowLine: 2", "firstRowLine: a");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "invalidFormat");
            System.out.println(onlyError.getMessage());
        }
    }

//missingRequiredExpression
//illegalGroovyExpression
//unknownCheckerName

    @Test
    public void testCsvBoundToUnknownVariable() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("header: \"typeSite\"\n" +
                    "          boundTo:\n" +
                    "            variable: localization", "header: \"typeSite\"\n" +
                    "          boundTo:\n" +
                    "            variable: localizations");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "csvBoundToUnknownVariable");
            System.out.println(onlyError.getMessage());
        }
    }

    @Test
    public void testCsvBoundToUnknownVariableComponent() throws Exception {
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMigrationApplicationConfigurationResourceName(3))) {
            ConfigurationParsingResult configurationParsingResult = parseYaml("components:\n" +
                    "          site:", "components:\n" +
                    "          sites:");
            Assert.assertFalse(configurationParsingResult.isValid());
            ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
            Assert.assertTrue(onlyError.getMessage() == "csvBoundToUnknownVariableComponent");
            System.out.println(onlyError.getMessage());
        }
    }
}