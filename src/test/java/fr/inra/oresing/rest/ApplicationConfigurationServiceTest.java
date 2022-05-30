package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.CheckerType;
import fr.inra.oresing.rest.exceptions.configuration.BadApplicationConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Ignore;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public static final Map<String, BadApplicationConfigurationException> configurationParsingResults = new HashMap<>();

    @AfterClass
    public static void registerErrors() throws IOException {
        final Map<String, ConfigurationParsingResult> collect = configurationParsingResults.entrySet()
                .stream().filter(e -> !e.getValue().getConfigurationParsingResult().isValid())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getConfigurationParsingResult()));
        final String errorsAsString = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(collect);
        File errorsFile = new File("ui/cypress/fixtures/applications/errors/errors.json");
        log.debug(errorsFile.getAbsolutePath());
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(errorsAsString);
        writer.close();
    }

    private ConfigurationParsingResult getConfigurationParsingResult(String methodName, byte[] bytes) {
        final ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
        try {
            BadApplicationConfigurationException.check(configurationParsingResult);
        } catch (BadApplicationConfigurationException e) {
            configurationParsingResults.put(methodName, e);
        }
        return configurationParsingResult;

    }

    private ConfigurationParsingResult parseYaml(String methodName, String toReplace, String by) {
        ConfigurationParsingResult configurationParsingResult;
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getValidationApplicationConfigurationResourceName())) {
            String yaml = IOUtils.toString(configurationFile, StandardCharsets.UTF_8);
            String wrongYaml = yaml.replace(toReplace, by);
            byte[] bytes = wrongYaml.getBytes(StandardCharsets.UTF_8);
            configurationParsingResult = service.parseConfigurationBytes(bytes);
            try {
                BadApplicationConfigurationException.check(configurationParsingResult);
            } catch (BadApplicationConfigurationException e) {
                configurationParsingResults.put(methodName, e);
            }
            return configurationParsingResult;
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }
    }

    @Test
    public void parseConfigurationFile() {
        ImmutableSet.of(
                //fixtures.getMonsoreApplicationConfigurationResourceName(),
                fixtures.getAcbbApplicationConfigurationResourceName(),
                fixtures.getOlaApplicationConfigurationResourceName(),
                fixtures.getHauteFrequenceApplicationConfigurationResourceName(),
                fixtures.getValidationApplicationConfigurationResourceName()
                //fixtures.getProApplicationConfigurationResourceName()
        ).forEach(resource -> {
            parseConfigurationFromResource(resource);
        });

        Assert.assertFalse(service.parseConfigurationBytes("vers: 0".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertTrue(service.parseConfigurationBytes("version: 1".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("version: 2".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("::".getBytes(StandardCharsets.UTF_8)).isValid());
    }

    private void parseConfigurationFromResource(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            byte[] bytes = in.readAllBytes();
            ConfigurationParsingResult configurationParsingResult = service.parseConfigurationBytes(bytes);
            log.debug("résultat de la validation de " + resource + " = " + configurationParsingResult);
            Assert.assertTrue(resource + " doit être reconnu comme un fichier valide", configurationParsingResult.isValid());
        } catch (IOException e) {
            throw new OreSiTechnicalException("ne peut pas lire le fichier de test " + resource, e);
        }
    }

    @Test
    public void testEmptyFile() {
        byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
        ConfigurationParsingResult configurationParsingResult = getConfigurationParsingResult("testEmptyFile", bytes);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("emptyFile", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingReferenceForChecker", "refType: sites", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult missingReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "missingReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeNull = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeNull".equals(vcr.getMessage()));

        Assert.assertEquals(true, missingReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeNull != null);
    }

    @Test
    public void testMissingVariableComponentForUniqueness() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingVariableComponentForUniqueness",
                "- variable: date\n" +
                        "        component: day",
                "- variable: date\n" +
                        "        component: jour"
        );
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult missingVariableComponentForChecker = Iterables.find(validationCheckResults, vcr -> "unknownUsedAsVariableComponentUniqueness".equals(vcr.getMessage()));

        final Set<String> unknownUsedAsVariableComponentUniqueness = (Set<String>) missingVariableComponentForChecker.getMessageParams().get("unknownUsedAsVariableComponentUniqueness");
        Assert.assertEquals(true, unknownUsedAsVariableComponentUniqueness != null);
        Assert.assertEquals(true, unknownUsedAsVariableComponentUniqueness.contains("date_jour"));
    }

    @Test
    public void testMissingInternationalizedColumn() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingInternationalizedColumn", "internationalizedColumns:\n" +
                "      nom du projet_key:", "internationalizedColumns:\n" +
                "      nom du projet_unknown:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceForChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceForChecker", "refType: sites", "refType: sitee");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult unknownReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "unknownReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeUnknown = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeUnknown".equals(vcr.getMessage()));

        Assert.assertEquals(true, unknownReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeUnknown != null);
    }

    @Test
    public void testUnsupportedVersion() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnsupportedVersion", "version: 1", "version: -1");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unsupportedVersion", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceInCompositeReference", "- reference: typeSites", "- reference: typeDeSites");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceInCompositeReference", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceInCompositereference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingReferenceInCompositereference", "- reference: typeSites", "- reference: ");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceInCompositereference", onlyError.getMessage());
    }

    @Test
    public void testRequiredReferenceInCompositeReferenceForParentKeyColumn() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testRequiredReferenceInCompositeReferenceForParentKeyColumn", "- reference: typeSites", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("requiredReferenceInCompositeReferenceForParentKeyColumn", onlyError.getMessage());
    }

    @Test
    public void testRequiredParentKeyColumnInCompositeReferenceForReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testRequiredParentKeyColumnInCompositeReferenceForReference", "parentKeyColumn: \"nom du type de site\"\n" +
                "        ", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("requiredParentKeyColumnInCompositeReferenceForReference", onlyError.getMessage());
    }

    @Test
    public void testMissingParentColumnForReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingParentColumnForReferenceInCompositeReference", "- parentKeyColumn: \"nom du site\"", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        boolean hasError = configurationParsingResult.getValidationCheckResults()
                .stream()
                .anyMatch((validationCheckResult -> "missingParentColumnForReferenceInCompositeReference".equals(validationCheckResult.getMessage())));
        Assert.assertEquals(true, hasError);
    }

    @Test
    public void testMissingParentRecursiveKeyColumnForReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingParentRecursiveKeyColumnForReferenceInCompositeReference", "parentKeyColumn: \"nom du site\"\n" +
                "        ", "parentKeyColumn: \"nom du site\"\n" +
                "        parentRecursiveKey: \"nom du parent\"\n" +
                "        ");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingParentRecursiveKeyColumnForReferenceInCompositeReference", onlyError.getMessage());
    }

    @Test
    public void testUndeclaredDataGroupForVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUndeclaredDataGroupForVariable", "data:\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("testVariableInMultipleDataGroup", "data:\n" +
                "            - Couleur des individus", "data:\n" +
                "            - localization\n" +
                "            - Couleur des individus");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("variableInMultipleDataGroup", onlyError.getMessage());
    }

    @Test
    public void testRecordInvalidKeyColumns() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testRecordInvalidKeyColumns", "columns:\n" +
                "      nom du projet_key:", "columns:\n" +
                "      nom du Projet_key:");
        Assert.assertFalse(configurationParsingResult.isValid());
        long count = configurationParsingResult.getValidationCheckResults()
                .stream()
                .map(ValidationCheckResult::getMessage)
                .filter(mes -> mes.equals("invalidInternationalizedColumns") || mes.equals("invalidKeyColumns"))
                .count();
        Assert.assertEquals(2, count);
    }

    @Test
    @Ignore
    /**
     *  on peut omettre le timescope
     */
    public void testMissingTimeScopeVariableComponentKey() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingTimeScopeVariableComponentKey", "component: site\n" +
                "      timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "component: site\n");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingTimeScopeVariableComponentKey", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentKeyMissingVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeScopeVariableComponentKeyMissingVariable", "timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "timeScope:\n" +
                "        component: day");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentKeyMissingVariable", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentKeyUnknownVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeScopeVariableComponentKeyUnknownVariable", "timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "timeScope:\n" +
                "        variable: dates\n" +
                "        component: day");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentKeyUnknownVariable", onlyError.getMessage());
    }

    @Test
    public void testTimeVariableComponentKeyMissingComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeVariableComponentKeyMissingComponent", "timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "timeScope:\n" +
                "        variable: date\n" +
                "        component: ~");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeVariableComponentKeyMissingComponent", onlyError.getMessage());
    }

    @Test
    public void testTimeVariableComponentKeyUnknownComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeVariableComponentKeyUnknownComponent", "timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "timeScope:\n" +
                "        variable: date\n" +
                "        component: days");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeVariableComponentKeyUnknownComponent", onlyError.getMessage());
    }

    @Test
    public void testTimeScopeVariableComponentWrongChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeScopeVariableComponentWrongChecker", "checker:\n" +
                "              name: Date", "checker:\n" +
                "              name: Dates");
        Assert.assertFalse(configurationParsingResult.isValid());

        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidFormat", onlyError.getMessage());

        /*Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "invalidFormat".equals(e.getMessage()))
                .filter(e -> "Dates".equals(e.getMessageParams().get("value")))
                .filter(e -> " [RegularExpression, GroovyExpression, Reference, Float, Integer, Date]".equals(e.getMessageParams().get("authorizedValues")))
                .filter(e -> Integer.valueOf(131).equals(e.getMessageParams().get("lineNumber")))
                .filter(e -> Integer.valueOf(21).equals(e.getMessageParams().get("columnNumber")))
                .filter(e -> "dataTypes->site->data->date->components->day->checker->name".equals(e.getMessageParams().get("path")))
                .collect(Collectors.toList())
        );*/
    }

    @Test
    public void testTimeScopeVariableComponentPatternUnknown() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testTimeScopeVariableComponentPatternUnknown", "params:\n" +
                "                pattern: dd/MM/yyyy", "params:\n" +
                "                pattern: dd/MM");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentPatternUnknown", onlyError.getMessage());
    }

    @Test
    public void testUnrecognizedProperty() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnrecognizedProperty", "compositeReferences", "compositReference");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unrecognizedProperty", onlyError.getMessage());
        Assert.assertEquals(9, onlyError.getMessageParams().get("lineNumber"));
        Assert.assertEquals(3, onlyError.getMessageParams().get("columnNumber"));
        Assert.assertEquals("compositReference", onlyError.getMessageParams().get("unknownPropertyName"));
    }

    @Test
    public void testInvalidFormat() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidFormat", "firstRowLine: 3", "firstRowLine: pas_un_chiffre");
        Assert.assertFalse(configurationParsingResult.isValid());


        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidFormat", onlyError.getMessage());
        /*
        Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "invalidFormat".equals(e.getMessage()))
                .filter(e -> "pas_un_chiffre".equals(e.getMessageParams().get("value")))
                .filter(e -> "".equals(e.getMessageParams().get("authorizedValues")))
                .filter(e -> Integer.valueOf(190).equals(e.getMessageParams().get("lineNumber")))
                .filter(e -> Integer.valueOf(21).equals(e.getMessageParams().get("columnNumber")))
                .filter(e -> "dataTypes->site->format->firstRowLine".equals(e.getMessageParams().get("path")))
                .collect(Collectors.toList()));*/
    }

    @Test
    public void testMissingRequiredExpression() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingRequiredExpression", "\"true\"", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingRequiredExpressionForValidationRuleInDataType", onlyError.getMessage());
    }

    @Test
    public void testIllegalGroovyExpression() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testIllegalGroovyExpression", "\"true\"", "if(}");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("illegalGroovyExpressionForValidationRuleInDataType", onlyError.getMessage());
    }

    @Test
    public void testUnknownCheckerName() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownCheckerName", "name: GroovyExpression", "name: GroovyExpressions");
        Assert.assertFalse(configurationParsingResult.isValid());

        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidFormat", onlyError.getMessage());
        /*Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults().stream()
                .filter(e -> "invalidFormat".equals(e.getMessage()))
                .filter(e -> "GroovyExpressions".equals(e.getMessageParams().get("value")))
                .filter(e -> " [RegularExpression, GroovyExpression, Reference, Float, Integer, Date]".equals(e.getMessageParams().get("authorizedValues")))
                .filter(e -> Integer.valueOf(177).equals(e.getMessageParams().get("lineNumber")))
                .filter(e -> Integer.valueOf(17).equals(e.getMessageParams().get("columnNumber")))
                .filter(e -> "dataTypes->site->validations->exempledeDeRegleDeValidation->checker->name".equals(e.getMessageParams().get("path")))
                .collect(Collectors.toList()));*/
    }

    @Test
    public void testCsvBoundToUnknownVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testCsvBoundToUnknownVariable", "header: \"typeSite\"\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("testCsvBoundToUnknownVariableComponent", "components:\n" +
                "          site:", "components:\n" +
                "          sites:");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult authorizationVariableComponentKeyUnknownComponent = Iterables.find(validationCheckResults, vcr -> "authorizationVariableComponentKeyUnknownComponent".equals(vcr.getMessage()));
        ValidationCheckResult csvBoundToUnknownVariableComponent = Iterables.find(validationCheckResults, vcr -> "csvBoundToUnknownVariableComponent".equals(vcr.getMessage()));

        Assert.assertEquals(true, authorizationVariableComponentKeyUnknownComponent != null);
        Assert.assertEquals(true, csvBoundToUnknownVariableComponent != null);
    }

    @Test
    public void testInvalidKeyColumns() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidKeyColumns", "keyColumns: [nom du projet_key]", "keyColumns: [nom du projet_clé]");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidKeyColumns", onlyError.getMessage());
    }

    @Test
    public void testMissingColumnInInternationalizationDisplayPattern() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingColumnInInternationalizationDisplayPattern", "'{nom du site_fr}'", "'{nom du site}'");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("unknownUsedAsInternationalizedColumns")).contains("nom du site"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("knownColumns")).contains("nom du site_fr"));
    }

    @Test
    public void testUnknownReferenceInInternationalizationDisplayPatternInDatatype() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceInInternationalizationDisplayPatternInDatatype", "internationalizationDisplays:\n" +
                "      sites:", "internationalizationDisplays:\n" +
                "      plateforme:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceInDatatypeReferenceDisplay", onlyError.getMessage());
    }

    @Test
    public void testMissingColumnInInternationalizationDisplayPatternInDatatype() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingColumnInInternationalizationDisplayPatternInDatatype", "'{nom du site_fr}'", "'{nom du site}'");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("unknownUsedAsInternationalizedColumns")).contains("nom du site"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("knownColumns")).contains("nom du site_fr"));
    }

    @Test
    public void testUndeclaredValueForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUndeclaredValueForChart", "value: \"value\"", "value: null");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unDeclaredValueForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("components")).equals(Set.of("value", "unit", "standardDeviation")));
    }

    @Test
    public void testMissingValueComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingValueComponentForChart", "value: \"value\"", "value: \"nonvalue\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingValueComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("valueComponent")).equals("nonvalue"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("components")).equals(Set.of("value", "unit", "standardDeviation")));
    }

    @Test
    public void testMissingAggregationVariableForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingAggregationVariableForChart", "aggregation:\n" +
                "            variable: Couleur des individus\n" +
                "            component: value", "aggregation:\n" +
                "            variable: pasdevariable\n" +
                "            component: value");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingAggregationVariableForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("aggregationVariable")).equals("pasdevariable"));
        Assert.assertTrue((onlyError.getMessageParams().get("aggregationComponent")).equals("value"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("variables")).equals(Set.of("date", "localization", "Couleur des individus", "Nombre d'individus")));
    }

    @Test
    public void testMissingAggregationComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingAggregationComponentForChart", "aggregation:\n" +
                "            variable: Couleur des individus\n" +
                "            component: value", "aggregation:\n" +
                "            variable: Couleur des individus\n" +
                "            component: pasdevalue");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingAggregationComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("aggregationVariable")).equals("Couleur des individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("aggregationComponent")).equals("pasdevalue"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("components")).equals(Set.of("value", "unit", "standardDeviation")));
    }

    @Test
    public void testMissingStandardDeviationComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingStandardDeviationComponentForChart", "standardDeviation: \"standardDeviation\"", "standardDeviation: \"badstandardDeviation\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingStandardDeviationComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("standardDeviation")).equals("badstandardDeviation"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("components")).equals(Set.of("value", "unit", "standardDeviation")));
    }

    @Test
    public void testMissingUnitComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingUnitComponentForChart", "unit: \"unit\"", "unit: \"badunit\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingUnitComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("unit")).equals("badunit"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("components")).equals(Set.of("value", "unit", "standardDeviation")));
    }

    @Test
    public void testvalid() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("", "", "");
        Assert.assertTrue(configurationParsingResult.isValid());
    }

    @Test
    public void testMissingKeyColumnsForReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingKeyColumnsForReference", "keyColumns: [nom du projet_key]", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingKeyColumnsForReference", onlyError.getMessage());
        Assert.assertEquals("projets", onlyError.getMessageParams().get("reference"));
    }

    @Test
    public void testIllegalCheckerConfigurationParameterForVariableComponentChecker() {
        String toReplace = "checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: dd/MM/yyyy";
        String replacement = "checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: dd/MM/yyyy\n" +
                "                refType: peu_importe_refType_n_a_pas_de_sens";
        ConfigurationParsingResult configurationParsingResult = parseYaml("testIllegalCheckerConfigurationParameterForVariableComponentChecker", toReplace, replacement);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("illegalCheckerConfigurationParameterForVariableComponentChecker", onlyError.getMessage());
        Assert.assertEquals("site", onlyError.getMessageParams().get("dataType"));
        Assert.assertEquals("date", onlyError.getMessageParams().get("datum"));
        Assert.assertEquals("day", onlyError.getMessageParams().get("component"));
        Assert.assertEquals(CheckerType.Date, onlyError.getMessageParams().get("checkerName"));
        Assert.assertEquals("refType", onlyError.getMessageParams().get("parameterName"));
    }

    @Test
    public void testauthorizationScopeMissingReferenceCheckerForAuthorizationScope() {
        String toReplace = "checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: sites";
        String replacement = "";
        ConfigurationParsingResult configurationParsingResult = parseYaml("testauthorizationScopeMissingReferenceCheckerForAuthorizationScope", toReplace, replacement);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationScopeMissingReferenceCheckerForAuthorizationScope", onlyError.getMessage());
        Assert.assertEquals("localization", onlyError.getMessageParams().get("authorizationScopeName"));
        Assert.assertEquals("site", onlyError.getMessageParams().get("dataType"));
        Assert.assertEquals("localization", onlyError.getMessageParams().get("variable"));
        Assert.assertEquals("site", onlyError.getMessageParams().get("component"));
    }

    @Test
    public void testAuthorizationScopeVariableComponentKeyMissingVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationScopeVariableComponentKeyMissingVariable", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          component: site");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationScopeVariableComponentKeyMissingVariable", onlyError.getMessage());
    }

    @Test
    public void testAuthorizationScopeVariableComponentKeyUnknownVariable() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationScopeVariableComponentKeyUnknownVariable", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localizations\n" +
                "          component: site");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationScopeVariableComponentKeyUnknownVariable", onlyError.getMessage());
    }

    @Test
    public void testAuthorizationVariableComponentKeyMissingComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationVariableComponentKeyMissingComponent", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationVariableComponentKeyMissingComponent", onlyError.getMessage());
    }

    @Test
    public void testAuthorizationVariableComponentKeyUnknownComponent() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationVariableComponentKeyUnknownComponent", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site", "\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: sites");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationVariableComponentKeyUnknownComponent", onlyError.getMessage());
    }

    @Test
    public void testMissingColumnReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingColumnReferenceForCheckerInReference", "description_en:\n" +
                "  sites:", "description_en:\n" +
                "  sites:\n" +
                "    validations:\n" +
                "      typeSitesRef:\n" +
                "        internationalizationName:\n" +
                "          fr: référence au type de site\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: typeSites\n" +
                "        columns: [ nom_key ]");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingColumnReferenceForCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingReferenceForCheckerInReference", "description_en:\n" +
                "  sites:", "description_en:\n" +
                "  sites:\n" +
                "    validations:\n" +
                "      typeSitesRef:\n" +
                "        internationalizationName:\n" +
                "          fr: référence au type de site\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "        columns: [ nom du type de site ]");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceForCheckerInReference", onlyError.getMessage());
    }


    @Test
    public void testUnknownReferenceForCheckerAndauthorizationScopeVariableComponentReftypeUnknown() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceForCheckerAndauthorizationScopeVariableComponentReftypeUnknown", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: sites", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: site");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult unknownReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "unknownReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeUnknown = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeUnknown".equals(vcr.getMessage()));

        Assert.assertEquals(true, unknownReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeUnknown != null);
    }

    @Test
    public void testInvalidPatternForReferenceColumnDateChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidPatternForReferenceColumnDateChecker", "columns:\n" +
                "      nom du projet_key:", "columns:\n" +
                "      nom du projet_key:\n" +
                "      Date:\n" +
                "        checker:\n" +
                "          name: Date\n" +
                "          params:\n" +
                "            pattern: coucou");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidPatternForReferenceColumnDateChecker", onlyError.getMessage());
    }

    @Test
    public void testInvalidPatternForDateCheckerForValidationRuleInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidPatternForDateCheckerForValidationRuleInReference", "description_en:\n" +
                "  sites:", "description_en:\n" +
                "  sites:\n" +
                "    validations:\n" +
                "      typeSitesRef:\n" +
                "        internationalizationName:\n" +
                "          fr: référence au type de site\n" +
                "        checker:\n" +
                "          name: Date\n" + "\n" +
                "          params:\n" +
                "            pattern: coucuo\n" +
                "        columns: [ nom du type de site ]");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidPatternForDateCheckerForValidationRuleInReference", onlyError.getMessage());
    }

    @Test
    public void testInvalidPatternForVariableComponentDateChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidPatternForVariableComponentDateChecker", "time:\n" +
                "            checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: HH:mm:ss", "time:\n" +
                "            checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: coucou");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidPatternForVariableComponentDateChecker", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForCheckerInReferenceColumn() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingReferenceForCheckerInReferenceColumn", "altitude:\n" +
                "      nom du type de plateforme:", "altitude:\n" +
                "      nom du type de plateforme:\n" +
                "        checker:\n" +
                "          name: Reference");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceForCheckerInReferenceColumn", onlyError.getMessage());
    }

    @Test
    public void testMissingParentLineInRecursiveReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingParentLineInRecursiveReference", "nom du taxon superieur:", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult missingParentRecursiveKeyColumnForReferenceInCompositeReference = Iterables.find(validationCheckResults, vcr -> "missingParentRecursiveKeyColumnForReferenceInCompositeReference".equals(vcr.getMessage()));
        ValidationCheckResult missingColumnReferenceForCheckerInReference = Iterables.find(validationCheckResults, vcr -> "missingColumnReferenceForCheckerInReference".equals(vcr.getMessage()));

        Assert.assertEquals(true, missingParentRecursiveKeyColumnForReferenceInCompositeReference != null);
        Assert.assertEquals(true, missingColumnReferenceForCheckerInReference != null);
    }

    @Test
    public void testMissingParamColumnReferenceForCheckerInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingParamColumnReferenceForCheckerInReference", "refType: taxon\n" +
                "        columns: [ nom du taxon superieur ]", "refType: taxon\n" +
                "        columns: ");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingParamColumnReferenceForCheckerInReference", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceForCheckerInDataType() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingReferenceForCheckerInDataType", "typeSite:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: typeSites", "typeSite:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceForChecker", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceForCheckerInDataType() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceForCheckerInDataType", "typeSite:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: typeSites", "typeSite:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: typeSite");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceForChecker", onlyError.getMessage());
    }

    @Test
    @Ignore
    /**
     *  on peut omettre authorisation
     */
    public void testMissingAuthorizationForDatatype() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingAuthorizationForDatatype", "authorization:\n" +
                "      dataGroups:\n" +
                "        referentiel:\n" +
                "          label: \"Référentiel\"\n" +
                "          data:\n" +
                "            - localization\n" +
                "            - date\n" +
                "        qualitatif:\n" +
                "          label: \"Données qualitatives\"\n" +
                "          data:\n" +
                "            - Couleur des individus\n" +
                "            - Nombre d'individus\n" +
                "      authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site\n" +
                "      timeScope:\n" +
                "        variable: date\n" +
                "        component: day", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingAuthorizationForDatatype", onlyError.getMessage());
    }

    @Test
    @Ignore
    /**
     *  on peut omettre authorisationScopes
     */
    public void testMissingAuthorizationScopeVariableComponentKey() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testMissingAuthorizationScopeVariableComponentKey", "authorizationScopes:\n" +
                "        localization:\n" +
                "          variable: localization\n" +
                "          component: site", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingAuthorizationScopeVariableComponentKey", onlyError.getMessage());
    }

    @Test
    public void testAuthorizationScopeVariableComponentReftypeNull() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationScopeVariableComponentReftypeNull", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: sites", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType:");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult missingReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "missingReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeNull = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeNull".equals(vcr.getMessage()));

        Assert.assertEquals(true, missingReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeNull != null);
    }

    @Test
    public void testAuthorizationScopeVariableComponentWrongChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testAuthorizationScopeVariableComponentWrongChecker", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: sites", "components:\n" +
                "          site:\n" +
                "            checker:\n" +
                "              name: Integer\n" +
                "              params:\n" +
                "                refType: sites");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult illegalCheckerConfigurationParameterForVariableComponentChecker = Iterables.find(validationCheckResults, vcr -> "illegalCheckerConfigurationParameterForVariableComponentChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentWrongChecker = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentWrongChecker".equals(vcr.getMessage()));

        Assert.assertEquals(true, illegalCheckerConfigurationParameterForVariableComponentChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentWrongChecker != null);
    }

    @Test
    public void testIllegalCheckerConfigurationParameterForReferenceColumnChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testIllegalCheckerConfigurationParameterForReferenceColumnChecker", "nom du type de plateforme:\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: platform_type\n" +
                "            required: true\n" +
                "            transformation:\n" +
                "              codify: true", "nom du type de plateforme:\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refTypes: platform_type");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unrecognizedProperty", onlyError.getMessage());
    }

    @Test
    public void testIllegalCheckerConfigurationParameterForValidationRuleInReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testIllegalCheckerConfigurationParameterForValidationRuleInReference", "floats:\n" +
                "        internationalizationName:\n" +
                "          fr: les décimaux\n" +
                "        columns: [ isFloatValue ]\n" +
                "        checker:\n" +
                "          name: Float", "floats:\n" +
                "        internationalizationName:\n" +
                "          fr: les décimaux\n" +
                "        columns: [ isFloatValue ]\n" +
                "        checker:\n" +
                "          name: Flaot");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidFormat", onlyError.getMessage());
    }

    @Test
    public void testInvalidDurationForVariableComponentDateChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidDurationForVariableComponentDateChecker", "checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: \"dd/MM/yyyy HH:mm:ss\"\n" +
                "                duration: \"1 MINUTES\"", "checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: \"dd/MM/yyyy HH:mm:ss\"\n" +
                "                duration: \"X MINUTES\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidDurationForVariableComponentDateChecker", onlyError.getMessage());
    }

    @Test
    public void testInvalidDurationForReferenceColumnDateChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidDurationForReferenceColumnDateChecker", "columns:\n" +
                "      Date:\n" +
                "        checker:\n" +
                "          name: Date\n" +
                "          params:\n" +
                "            pattern: dd/MM/yyyy\n" +
                "            duration: \"1 MINUTES\"\n" +
                "            required: true", "columns:\n" +
                "      Date:\n" +
                "        checker:\n" +
                "          name: Date\n" +
                "          params:\n" +
                "            pattern: dd/MM/yyyy\n" +
                "            duration: \"x MINUTES\"\n" +
                "            required: true");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidDurationForReferenceColumnDateChecker", onlyError.getMessage());
    }

    @Test
    public void testInvalidPatternForDateCheckerForValidationRuleInDataType() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testInvalidPatternForDateCheckerForValidationRuleInDataType", "components:\n" +
                "          day:\n" +
                "            checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: dd/MM/yyyy", "components:\n" +
                "          day:\n" +
                "            checker:\n" +
                "              name: Date\n" +
                "              params:\n" +
                "                pattern: dd/MM/YY");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentPatternUnknown", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceForCheckerInReferenceColumn() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("testUnknownReferenceForCheckerInReferenceColumn", "nom du type de plateforme:\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            refType: platform_type\n" +
                "            required: true", "nom du type de plateforme:\n" +
                "        checker:\n" +
                "          name: Reference\n" +
                "          params:\n" +
                "            name: Floatt");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unrecognizedProperty", onlyError.getMessage());
    }
}
