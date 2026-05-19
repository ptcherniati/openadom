package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Tag("domain.model")
@Tag("docker-required")
@Tag("GENERATE_CYPRESS_FIXTURES")
public class TestReferencesErrors extends AbstractIntegrationTest {

    public static final Map<String, String> responses = new HashMap<>();
    public static final String PASSWORD = "xxxxxxxx";
    private static final ObjectMapper mapper = new ObjectMapper();

    private Fixtures fixtures;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Écrit les erreurs collectées dans {@code ref_ola_errors.json} <strong>uniquement</strong>
     * si la propriété système
     * {@value fr.inra.oresing.rest.fixtures.CypressFixtureWriter#BASE_DIR_PROPERTY}
     * est explicitement définie.
     *
     * <p>Sans cette propriété, la méthode ne fait rien : les fichiers de fixtures ne doivent être
     * générés que lors d'une exécution dédiée (tag {@code GENERATE_CYPRESS_FIXTURES} ou
     * {@code -Dcypress.fixtures.base.dir=<dir>}), jamais pendant une batterie de tests ordinaire.
     *
     * <p>Le contenu est passé par {@link fr.inra.oresing.rest.fixtures.CypressFixtureWriter}
     * afin que les timestamps volatils (champ {@code time} dans les réponses NDJSON de validation)
     * soient normalisés et que le fichier soit stable d'un run à l'autre.
     */
    @AfterAll
    static void registerErrors() throws IOException {
        String baseDirProp = System.getProperty(fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY);
        if (baseDirProp == null) {
            log.debug("registerErrors ignoré (propriété {} non définie)",
                    fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY);
            return;
        }
        String errorsAsString = new ObjectMapper().writeValueAsString(responses);
        // Normalisation via CypressFixtureWriter : timestamps volatils → valeur stable
        fr.inra.oresing.rest.fixtures.CypressFixtureWriter writer =
                new fr.inra.oresing.rest.fixtures.CypressFixtureWriter(java.nio.file.Paths.get(baseDirProp));
        writer.write("ui/cypress/fixtures/applications/errors/ref_ola_errors.json", errorsAsString);
        log.info("register errors file (sanitisé) : {}/ui/cypress/fixtures/applications/errors/ref_ola_errors.json",
                baseDirProp);
    }

    public static Stream<RecursivityTestCase> getRecursiviteReferentielErrorsStringReplace() {
        return Fixtures.getRecursiviteReferentielErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
    }

    public static Stream<RecursivityTestCase> getParametresMesuresReferentielErrorsStringReplace() {
        return Fixtures.getParametresMesuresReferentielErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
    }

    public static Stream<RecursivityTestCase> getRecursiviteDataErrorsStringReplace() {
        return Fixtures.getRecursiviteDataErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
    }

    public static Stream<RecursivityTestCase> getRepeatedColumnsgWithAllowUnexpectedColumnsDataErrorsStringReplace() {
        return Fixtures.getRepeatedColumnsgWithAllowUnexpectedColumnsDataErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
    }

    public static Stream<RecursivityTestCase> getRepeatedColumnsDataErrorsStringReplace() {
        return Fixtures.getRepeatedColumnsDataErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
    }

    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(mockMvc, null, namedParameterJdbcTemplate, authenticationService);
    }

    @TestFactory
    @DisplayName("Tests des erreurs csv")
    Stream<DynamicNode> testRecursivity() throws IOException {
        final Fixtures.UserConnection recursivityConnection = initAndLoadRecursivity();
        final String proprieteTaxonCSV = loadProprieteTaxonCSV();

        final String site = "leman";
        final String monRepositoryCSV = getRepositoryCSV(Fixtures.getConditionsPrelevementRepositoryResourceName(site));
        return Stream.of(
                dynamicContainer(
                        "RecursiviteReferentielErrors",
                        getRecursiviteReferentielErrorsStringReplace()
                                .map(recursivityTestCase -> dynamicTest(
                                                recursivityTestCase.name(),
                                                () -> {
                                                    String response;
                                                    final String textCsvModify = proprieteTaxonCSV.replace(recursivityTestCase.replace(), recursivityTestCase.by());
                                                    try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                                                        final MockMultipartFile refFile = new MockMultipartFile("file", recursivityTestCase.name() + ".csv", "text/plain", refStream);
                                                        log.info(recursivityTestCase.name());
                                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", "proprietes_taxon").file(refFile)
                                                                        .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                                                                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
                                                        Assertions.assertEquals(mapper.readTree(recursivityTestCase.expectedResponse()), mapper.readTree(response));
                                                        responses.put(recursivityTestCase.name(), response);
                                                    }
                                                }
                                        )
                                )
                ),
                dynamicTest("load references",
                        () -> {
                            try {
                                loadRecursivityReferences(recursivityConnection);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                ),
                dynamicContainer(
                        "RecursiviteDataErrors",
                        getRecursiviteDataErrorsStringReplace()
                                .map(recursivityTestCase -> dynamicTest(
                                                recursivityTestCase.name(),
                                                () -> {
                                                    String response;
                                                    final String textCsvModify = monRepositoryCSV.replace(recursivityTestCase.replace(), recursivityTestCase.by());
                                                    try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                                                        final MockMultipartFile refFile = new MockMultipartFile("file", "suivi_des_lacs_leman_conditions_prelevements_01-01-2020_31-12-2020.csv", "text/plain", refStream);
                                                        log.info(recursivityTestCase.name());
                                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/condition_prelevements").file(refFile)
                                                                        .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                                                                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
                                                        final Matcher m = Pattern.compile("(.*)\"referenceValues\":(\\{(.*?)\\})(.*)").matcher(response);
                                                        responses.put(recursivityTestCase.name(), response);
                                                        if (m.matches()) {
                                                            response = String.format("%s[%s]%s", m.group(1), Arrays.stream(m.group(3).split(",")).map(s -> s.split(":")[0]).sorted().collect(Collectors.joining(",")), m.group(4));
                                                        }
                                                        Assertions.assertEquals(mapper.readTree(recursivityTestCase.expectedResponse()), mapper.readTree(response));
                                                    }
                                                }
                                         )
                                 )
                 ),
                dynamicContainer(
                        "ParametresMesuresErrors",
                        getParametresMesuresReferentielErrorsStringReplace()
                                .map(recursivityTestCase -> dynamicTest(
                                                recursivityTestCase.name(),
                                                () -> {
                                                    String response;
                                                    final String parametresMesuresCSV = getRepositoryCSV("/data/recursivite/parametres_mesures_test.csv");
                                                    final String textCsvModify = parametresMesuresCSV.replace(recursivityTestCase.replace(), recursivityTestCase.by());
                                                    try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                                                        final MockMultipartFile refFile = new MockMultipartFile("file", "parametres_mesures_test.csv", "text/plain", refStream);
                                                        log.info(recursivityTestCase.name());
                                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", "parametres_mesures").file(refFile)
                                                                        .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                                                                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
                                                        Assertions.assertEquals(mapper.readTree(recursivityTestCase.expectedResponse()), mapper.readTree(response));
                                                        responses.put(recursivityTestCase.name(), response);
                                                    }
                                                }
                                        )
                                )
                )
        );
    }

    @TestFactory
    @DisplayName("Tests des erreurs unexpected Columns")
    Stream<DynamicNode> testRepeatedColumnsWithAllowUnexpectedColumns() throws Exception {
        final Fixtures.UserConnection repeatedColumnsConnection = initRepeatedColumn();
        loadRepeatedColumn(repeatedColumnsConnection);
        loadRepeatedColumnRefrences(repeatedColumnsConnection);
        final String monRepositoryCSV = getRepositoryCSV(Fixtures.getSWCRepositoryResourceName());
        return getRepeatedColumnsgWithAllowUnexpectedColumnsDataErrorsStringReplace()
                .map(recursivityTestCase -> dynamicTest(
                                recursivityTestCase.name(),
                                () -> {
                                    String response;
                                    final String textCsvModify = monRepositoryCSV.replace(recursivityTestCase.replace(), recursivityTestCase.by());
                                    try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                                        final MockMultipartFile refFile = new MockMultipartFile("file", "SWC_truncated.csv", "text/plain", refStream);
                                        log.info(recursivityTestCase.name());
                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc").file(refFile)
                                                        .header("Authorization", "Bearer " + repeatedColumnsConnection.jwt()))
                                                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();

                                        Assertions.assertEquals(mapper.readTree(recursivityTestCase.expectedResponse()), mapper.readTree(response));
                                        responses.put(recursivityTestCase.name(), response);
                                    }
                                }
                        )
                );
    }

    private void loadRecursivityReferences(Fixtures.UserConnection recursivityConnection) throws Exception {
        String response;
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    private String loadProprieteTaxonCSV() throws IOException {
        final String proprietes_taxon_path = Fixtures.getRecursiviteReferentielOrderFiles().get("proprietes_taxon");
        final StringBuilder textBuilder = new StringBuilder();
        try (final InputStream refStream = getClass().getResourceAsStream(proprietes_taxon_path)) {
            assert refStream != null;
            try (final Reader reader = new BufferedReader(new InputStreamReader(refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuilder.append((char) c);
                }
            }
        }
        final String monCSV = textBuilder.toString();
        return monCSV;
    }

    private Fixtures.UserConnection initAndLoadRecursivity() {
        Fixtures.UserConnection recursivityConnection;
        final URL resource = getClass().getResource(Fixtures.getRecursivityApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            Fixtures.CreateUser recursivity = new Fixtures.CreateUser("recursivity", PASSWORD, "recursivity@inrae.fr");
            recursivityConnection = fixtures.createUserForUserDefinition(recursivity, true, false);
            fixtures.addUserRightCreateApplication(recursivityConnection.userResult().userId(), "recursivite");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, recursivityConnection.jwt(), "recursivite", ""));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite").param("filter", "ALL")
                            .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon"))).andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_"))).andExpect(jsonPath("$.configuration.i18n.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Taxa properties"))).andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        return recursivityConnection;
    }

    private String getRepositoryCSV(String SWCRepositoryResourceName) throws IOException {
        final URL resources = getClass().getResource(SWCRepositoryResourceName);
        final StringBuilder textBuild = new StringBuilder();
        try (final InputStream refStream = Objects.requireNonNull(resources).openStream()) {
            try (final Reader reader = new BufferedReader(new InputStreamReader(refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuild.append((char) c);
                }
            }
        }
        final String monRepositoryCSV = textBuild.toString();
        return monRepositoryCSV;
    }

    private Fixtures.UserConnection initRepeatedColumn() throws Exception {
        Fixtures.CreateUser repeatedcolumns = new Fixtures.CreateUser("repeatedcolumns", PASSWORD, "repeatedcolumns@inrae.fr");
        Fixtures.UserConnection repeatedcolumnsConnection = fixtures.createUserForUserDefinition(repeatedcolumns, true, false);
        fixtures.addUserRightCreateApplication(repeatedcolumnsConnection.userResult().userId(), "repeatedcolumns");
        return repeatedcolumnsConnection;
    }

    private void loadRepeatedColumnRefrences(Fixtures.UserConnection repeatedcolumnsConnection) throws Exception {
        String response;
        for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + repeatedcolumnsConnection.jwt()))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() > 300) {
                                log.error(e.getKey());
                            }
                        }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    private void loadRepeatedColumn(Fixtures.UserConnection repeatedcolumnsConnection) {
        final URL resource = getClass().getResource(Fixtures.getRepeatedColumnsWithAllowUnexpectedColumnsApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "repeatedcolumns.yaml", "text/plain", in);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedcolumnsConnection.jwt(), "repeatedcolumns", ""));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/repeatedcolumns").param("filter", "ALL")
                            .header("Authorization", "Bearer " + repeatedcolumnsConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    @TestFactory
    @DisplayName("Tests des erreurs sus les repeated Columns")
    Stream<DynamicNode> repeatedColumnsTest() throws Exception {
        final Fixtures.UserConnection repeatedcolumnsConnection = initAndLoadRepeatedColumn();
        final String monRepositoryCSV = getRepositoryCSV(Fixtures.getSWCRepositoryResourceName());
        return getRepeatedColumnsDataErrorsStringReplace()
                .map(recursivityTestCase -> {
                    return dynamicTest(recursivityTestCase.name(), () -> {
                        String response;
                        final String textCsvModify = monRepositoryCSV.replace(recursivityTestCase.replace(), recursivityTestCase.by());
                        try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                            final MockMultipartFile refFile = new MockMultipartFile("file", "SWC_truncated.csv", "text/plain", refStream);
                            log.info(recursivityTestCase.name());
                            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc").file(refFile)
                                            .header("Authorization", "Bearer " + repeatedcolumnsConnection.jwt()))
                                    .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();

                            assertEquals(recursivityTestCase.expectedResponse(), response);
                            responses.put(recursivityTestCase.name(), response);
                        }
                    });
                });
    }

    private Fixtures.UserConnection initAndLoadRepeatedColumn() throws Exception {
        Fixtures.CreateUser repeatedcolumns = new Fixtures.CreateUser("repeatedcolumns", PASSWORD, "repeatedcolumns@inrae.fr");
        Fixtures.UserConnection repeatedcolumnsConnection = fixtures.createUserForUserDefinition(repeatedcolumns, true, false);
        fixtures.addUserRightCreateApplication(repeatedcolumnsConnection.userResult().userId(), "repeatedcolumns");

        String response;
        final URL resource = getClass().getResource(Fixtures.getRepeatedColumnsApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "repeatedcolumns.yaml", "text/plain", in);
            // Charger l'application une seule fois (pas à chaque itération pour éviter la race condition
            // entre le sink async de la config précédente et le ReactiveTypeProgress(0D) du suivant)
            fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedcolumnsConnection.jwt(), "repeatedcolumns", ""));

            for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey()).file(refFile)
                                    .header("Authorization", "Bearer " + repeatedcolumnsConnection.jwt()))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() > 300) {
                                    log.error(e.getKey());
                                }
                            }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        return repeatedcolumnsConnection;
    }

    record RecursivityTestCase(String name, String replace, String by, String expectedResponse) {
        static RecursivityTestCase of(Map.Entry<String, List<String>> entry) {
            return new RecursivityTestCase(entry.getKey(), entry.getValue().get(0), entry.getValue().get(1), entry.getValue().get(2));
        }
    }

    @TestFactory
    @DisplayName("Tests de l'erreur missingrecursiveParentReferenceWithComponent")
    Stream<DynamicNode> testMissingParentInRecursiveReference() throws Exception {
        final Fixtures.UserConnection recursivityConnection = initAndLoadRecursivity();
        // Charger site et proprietes_taxon seulement (pas taxon, c'est ce qu'on va tester)
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            if ("taxon".equals(e.getKey())) {
                continue; // on ne charge pas le taxon ici : c'est lui qu'on va tester avec erreur
            }
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                        .andExpect(status().isCreated());
            }
        }

        // Lire le CSV des taxons et remplacer un taxon supérieur par un nom inexistant
        final String taxonCsvPath = Fixtures.getRecursiviteReferentielOrderFiles().get("taxon");
        final String originalTaxonCsv = getRepositoryCSV(taxonCsvPath);
        // `;Chroomonadaceae;` apparaît dans la colonne "taxon_superieur" (colonne 4) des lignes 2 et 3
        // mais pas dans la colonne "nom" (colonne 1) du taxon Chroomonadaceae (ligne 4 : pas de `;` avant)
        // Donc ce remplacement modifie les références au parent sans toucher à la définition du parent lui-même
        final String modifiedTaxonCsv = originalTaxonCsv.replace(";Chroomonadaceae;", ";TaxonParentInexistant;");

        return Stream.of(
                dynamicTest("missingrecursiveParentReferenceWithComponent", () -> {
                    try (final InputStream refStream = new ByteArrayInputStream(modifiedTaxonCsv.getBytes(StandardCharsets.UTF_8))) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", "taxons_invalid_parent.csv", "text/plain", refStream);
                        log.info("missingrecursiveParentReferenceWithComponent");
                        final String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", "taxon").file(refFile)
                                        .header("Authorization", "Bearer " + recursivityConnection.jwt()))
                                .andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$[0].message", IsEqual.equalTo("missingrecursiveParentReferenceWithComponent")))
                                .andReturn().getResponse().getContentAsString();
                        responses.put("missingrecursiveParentReferenceWithComponent", response);
                        log.info("missingrecursiveParentReferenceWithComponent response: {}", response);
                    }
                })
        );
    }

    @TestFactory
    @DisplayName("Tests des erreurs de configuration Groovy")
    Stream<DynamicNode> testGroovyConfigurationErrors() throws Exception {
        final Fixtures.CreateUser groovyTestUser = new Fixtures.CreateUser("recursivity_groovy", PASSWORD, "recursivity_groovy@inrae.fr");
        final Fixtures.UserConnection groovyConnection = fixtures.createUserForUserDefinition(groovyTestUser, true, false);
        fixtures.addUserRightCreateApplication(groovyConnection.userResult().userId(), "recursivite_groovy");

        return Stream.of(
                dynamicTest("badGroovyExpression", () -> {
                    final URL resource = getClass().getResource(Fixtures.getRecursivityApplicationWithBadGroovyResourceName());
                    try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "recusivite-bad-groovy.yaml", "text/plain", in);
                        final MvcResult mvcResult = fixtures.validateApplication(configuration, groovyConnection.jwt());
                        final String responseContent = mvcResult.getResponse().getContentAsString();
                        Assertions.assertTrue(
                                responseContent.contains("badGroovyExpression"),
                                "La réponse devrait contenir 'badGroovyExpression'. Réponse obtenue : " + responseContent
                        );
                        responses.put("badGroovyExpression", responseContent);
                        log.info("badGroovyExpression response: {}", responseContent);
                    }
                })
        );
    }
}