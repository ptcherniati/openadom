package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test
    public void parseConfigurationFile() {
        ImmutableSet.of(
                fixtures.getMonsoreApplicationConfigurationResourceName(),
                fixtures.getAcbbApplicationConfigurationResourceName(),
                fixtures.getOlaApplicationConfigurationResourceName(),
                fixtures.getHauteFrequenceApplicationConfigurationResourceName(),
                fixtures.getValidationApplicationConfigurationResourceName()
                //fixtures.getProApplicationConfigurationResourceName()
        ).forEach(resource -> {
            parseConfigurationFromResource(resource);
        });

        Assert.assertFalse(service.parseConfigurationBytes("vers: 0".getBytes(StandardCharsets.UTF_8)).isValid());
        Assert.assertFalse(service.parseConfigurationBytes("version: 1".getBytes(StandardCharsets.UTF_8)).isValid());
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult missingReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "missingReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeNull = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeNull".equals(vcr.getMessage()));

        Assert.assertEquals(true, missingReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeNull != null);
    }

    @Test
    public void testMissingVariableComponentForUniqueness() {
        ConfigurationParsingResult configurationParsingResult = parseYaml(
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("internationalizedColumns:\n" +
                "      nom du projet_key:", "internationalizedColumns:\n" +
                "      nom du projet_unknown:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
    }

    @Test
    public void testUnknownReferenceForChecker() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("refType: sites", "refType: sitee");
        Assert.assertFalse(configurationParsingResult.isValid());
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult unknownReferenceForChecker = Iterables.find(validationCheckResults, vcr -> "unknownReferenceForChecker".equals(vcr.getMessage()));
        ValidationCheckResult authorizationScopeVariableComponentReftypeUnknown = Iterables.find(validationCheckResults, vcr -> "authorizationScopeVariableComponentReftypeUnknown".equals(vcr.getMessage()));

        Assert.assertEquals(true, unknownReferenceForChecker != null);
        Assert.assertEquals(true, authorizationScopeVariableComponentReftypeUnknown != null);
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
    public void testUnknownReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("- reference: typeSites", "- reference: typeDeSites");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceInCompositeReference", onlyError.getMessage());
    }

    @Test
    public void testMissingReferenceInCompositereference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("- reference: typeSites", "- reference: ");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingReferenceInCompositereference", onlyError.getMessage());
    }

    @Test
    public void testRequiredReferenceInCompositeReferenceForParentKeyColumn() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("- reference: typeSites", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("requiredReferenceInCompositeReferenceForParentKeyColumn", onlyError.getMessage());
    }

    @Test
    public void testRequiredParentKeyColumnInCompositeReferenceForReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("parentKeyColumn: \"nom du type de site\"\n" +
                "        ", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("requiredParentKeyColumnInCompositeReferenceForReference", onlyError.getMessage());
    }

    @Test
    public void testMissingParentColumnForReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("- parentKeyColumn: \"nom du site\"", "");
        Assert.assertFalse(configurationParsingResult.isValid());
        boolean hasError = configurationParsingResult.getValidationCheckResults()
                .stream()
                .anyMatch((validationCheckResult -> "missingParentColumnForReferenceInCompositeReference".equals(validationCheckResult.getMessage())));
        Assert.assertEquals(true, hasError);
    }

    @Test
    public void testMissingParentRecursiveKeyColumnForReferenceInCompositeReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("parentKeyColumn: \"nom du site\"\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("columns:\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("component: site\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("timeScope:\n" +
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
                "                pattern: dd/MM/yyyy", "params:\n" +
                "                pattern: dd/MM");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("timeScopeVariableComponentPatternUnknown", onlyError.getMessage());
    }

    @Test
    public void testUnrecognizedProperty() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("compositeReferences", "compositReference");
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
        ConfigurationParsingResult configurationParsingResult = parseYaml("firstRowLine: 3", "firstRowLine: pas_un_chiffre");
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
        Assert.assertEquals("missingRequiredExpressionForValidationRuleInDataType", onlyError.getMessage());
    }

    @Test
    public void testIllegalGroovyExpression() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("\"true\"", "if(}");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("illegalGroovyExpressionForValidationRuleInDataType", onlyError.getMessage());
    }

    @Test
    public void testUnknownCheckerName() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("name: GroovyExpression", "name: GroovyExpressions");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownCheckerNameForValidationRuleInDataType", onlyError.getMessage());
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
        List<ValidationCheckResult> validationCheckResults = configurationParsingResult.getValidationCheckResults();
        ValidationCheckResult authorizationVariableComponentKeyUnknownComponent = Iterables.find(validationCheckResults, vcr -> "authorizationVariableComponentKeyUnknownComponent".equals(vcr.getMessage()));
        ValidationCheckResult csvBoundToUnknownVariableComponent = Iterables.find(validationCheckResults, vcr -> "csvBoundToUnknownVariableComponent".equals(vcr.getMessage()));

        Assert.assertEquals(true, authorizationVariableComponentKeyUnknownComponent != null);
        Assert.assertEquals(true, csvBoundToUnknownVariableComponent != null);
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
    public void testMissingColumnInInternationalizationDisplayPattern() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("'{nom du site_fr}'", "'{nom du site}'");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("unknownUsedAsInternationalizedColumns")).contains("nom du site"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("knownColumns")).contains("nom du site_fr"));
    }

    @Test
    public void testUnknownReferenceInInternationalizationDisplayPatternInDatatype() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("internationalizationDisplays:\n" +
                "      sites:", "internationalizationDisplays:\n" +
                "      plateforme:");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unknownReferenceInDatatypeReferenceDisplay", onlyError.getMessage());
    }

    @Test
    public void testMissingColumnInInternationalizationDisplayPatternInDatatype() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("'{nom du site_fr}'", "'{nom du site}'");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("invalidInternationalizedColumns", onlyError.getMessage());
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("unknownUsedAsInternationalizedColumns")).contains("nom du site"));
        Assert.assertTrue(((Set) onlyError.getMessageParams().get("knownColumns")).contains("nom du site_fr"));
    }

    @Test
    public void testUndeclaredValueForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("value: \"value\"", "value: null");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("unDeclaredValueForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("components")).equals(Set.of("value","unit","standardDeviation")));
    }

    @Test
    public void testMissingValueComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("value: \"value\"", "value: \"nonvalue\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingValueComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("valueComponent")).equals("nonvalue"));
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("components")).equals(Set.of("value","unit","standardDeviation")));
    }

    @Test
    public void testMissingAggregationVariableForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("aggregation:\n" +
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
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("variables")).equals(Set.of("date","localization","Couleur des individus","Nombre d'individus")));
    }

    @Test
    public void testMissingAggregationComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("aggregation:\n" +
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
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("components")).equals(Set.of("value","unit","standardDeviation")));
    }

    @Test
    public void testMissingStandardDeviationComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("standardDeviation: \"standardDeviation\"", "standardDeviation: \"badstandardDeviation\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingStandardDeviationComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("standardDeviation")).equals("badstandardDeviation"));
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("components")).equals(Set.of("value","unit","standardDeviation")));
    }

    @Test
    public void testMissingUnitComponentForChart() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("unit: \"unit\"", "unit: \"badunit\"");
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("missingUnitComponentForChart", onlyError.getMessage());
        Assert.assertTrue((onlyError.getMessageParams().get("variable")).equals("Nombre d'individus"));
        Assert.assertTrue((onlyError.getMessageParams().get("dataType")).equals("site"));
        Assert.assertTrue((onlyError.getMessageParams().get("unit")).equals("badunit"));
        Assert.assertTrue(((Set)onlyError.getMessageParams().get("components")).equals(Set.of("value","unit","standardDeviation")));
    }

    @Test
    public void testvalid(){
        ConfigurationParsingResult configurationParsingResult = parseYaml("","");
        Assert.assertTrue(configurationParsingResult.isValid());
    }

    @Test
    public void testMissingKeyColumnsForReference() {
        ConfigurationParsingResult configurationParsingResult = parseYaml("keyColumns: [nom du projet_key]", "");
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
        ConfigurationParsingResult configurationParsingResult = parseYaml(toReplace, replacement);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("illegalCheckerConfigurationParameterForVariableComponentChecker", onlyError.getMessage());
        Assert.assertEquals("site", onlyError.getMessageParams().get("dataType"));
        Assert.assertEquals("date", onlyError.getMessageParams().get("datum"));
        Assert.assertEquals("day", onlyError.getMessageParams().get("component"));
        Assert.assertEquals("Date", onlyError.getMessageParams().get("checkerName"));
        Assert.assertEquals("refType", onlyError.getMessageParams().get("parameterName"));
    }

    @Test
    public void testauthorizationScopeMissingReferenceCheckerForAuthorizationScope() {
        String toReplace = "checker:\n" +
                "              name: Reference\n" +
                "              params:\n" +
                "                refType: sites";
        String replacement = "";
        ConfigurationParsingResult configurationParsingResult = parseYaml(toReplace, replacement);
        Assert.assertFalse(configurationParsingResult.isValid());
        ValidationCheckResult onlyError = Iterables.getOnlyElement(configurationParsingResult.getValidationCheckResults());
        log.debug(onlyError.getMessage());
        Assert.assertEquals("authorizationScopeMissingReferenceCheckerForAuthorizationScope", onlyError.getMessage());
        Assert.assertEquals("localization", onlyError.getMessageParams().get("authorizationScopeName"));
        Assert.assertEquals("site", onlyError.getMessageParams().get("dataType"));
        Assert.assertEquals("localization", onlyError.getMessageParams().get("variable"));
        Assert.assertEquals("site", onlyError.getMessageParams().get("component"));
    }
}