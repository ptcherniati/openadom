package fr.inra.oresing.rest;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.fixtures.*;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static fr.inra.oresing.rest.Fixtures.testZip;
import static fr.inra.oresing.rest.fixtures.MonSoereFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@Tag("docker-required")
public class OreSiResourcesTest extends AbstractIntegrationTest {

    public static final String SELECT_ROW_BY_ID = """
                    {
                        "rowIds" : [
                             "%s"
                          ]
                    }
            """;
    public static final String SELECT_ROW_BY_NATURAL_KEY = """
                    {
                        "naturalKeys" : [
                             "%s"
                          ]
                    }
            """;
    public final Fixtures.CreateUser monsoresimple = new Fixtures.CreateUser("monsoresimple", "xxxxxxxx", "monsoresimple@inrae.fr");
    public final Fixtures.CreateUser withRightsUser = new Fixtures.CreateUser("withrigths", "xxxxxxxx", "withrigths@inrae.fr");
    @Autowired
    private DataSource dataSource;
    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Écrit {@code jsonContent} dans {@code filePath} <strong>uniquement</strong> si la propriété
     * système {@value fr.inra.oresing.rest.fixtures.CypressFixtureWriter#BASE_DIR_PROPERTY}
     * est explicitement définie.
     *
     * <p>Sans cette propriété, la méthode ne fait rien : les fichiers de fixtures ne doivent être
     * générés que lors d'une exécution dédiée (tag {@code GENERATE_CYPRESS_FIXTURES} ou
     * {@code -Dcypress.fixtures.base.dir=<dir>}), jamais pendant une batterie de tests ordinaire.
     * Cela évite que des fichiers {@code ui/cypress/fixtures/} non sanitisés apparaissent après
     * chaque run CI.
     *
     * <p>Le chemin est interprété comme relatif au répertoire défini par la propriété système.
     * Les répertoires parents sont créés à la volée si nécessaire.
     *
     * @param filePath    chemin relatif du fichier cible
     * @param jsonContent contenu à écrire
     */
    public static void registerFile(final String filePath, final String jsonContent) throws IOException {
        String baseDirProp = System.getProperty(fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY);
        if (baseDirProp == null) {
            log.debug("registerFile ignoré (propriété {} non définie) : {}", fr.inra.oresing.rest.fixtures.CypressFixtureWriter.BASE_DIR_PROPERTY, filePath);
            return;
        }
        log.info("register file {}/{}", baseDirProp, filePath);
        if (filePath.startsWith("ui/cypress/fixtures/")) {
            // Fichiers Cypress : sanitisation complète via CypressFixtureWriter
            // (normalisation UUID, canonicalisation JSON, normalisation timestamps)
            fr.inra.oresing.rest.fixtures.CypressFixtureWriter writer =
                    new fr.inra.oresing.rest.fixtures.CypressFixtureWriter(java.nio.file.Paths.get(baseDirProp));
            writer.write(filePath, jsonContent);
        } else {
            // Autres fichiers (openapi.yaml, …) : écriture brute, pas de transformation JSON
            final File outFile = new File(baseDirProp, filePath);
            if (outFile.getParentFile() != null) {
                outFile.getParentFile().mkdirs();
            }
            try (final BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
                bw.write(jsonContent);
            }
        }
    }

    private static InputStream changeToV2(final InputStream inputStream) throws IOException {
        assert inputStream != null;
        final String yaml = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        final String yamlVersion2 = yaml.replace("OA_version: 3.0.1", "OA_version: 3.0.2");
        return new ByteArrayInputStream(yamlVersion2.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @Tag("core.basic")
    public void testDatabaseUser() throws SQLException {
        String currentUser = getCurrentDatabaseUser();
        Assertions.assertEquals("openAdomTechUser", currentUser, "Le test devrait être exécuté en tant qu'openadomTechUser");
    }

    private String getCurrentDatabaseUser() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT CURRENT_USER")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        throw new SQLException("Impossible d'obtenir l'utilisateur actuel de la base de données");
    }

    @Test
    @Tag("core.basic")
    public void services_model() throws Exception {
        final String services_model = mockMvc.perform(get("/api-docs.yaml").accept(MediaType.parseMediaType("application/vnd.oai.openapi"))).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        registerFile("documentations/openapi.yaml", services_model);

    }

    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(mockMvc, userRepository, namedParameterJdbcTemplate, authenticationService);
    }

    @TestFactory
    @DisplayName("Tests de l'application MONSOERE")
    @Tag("OTHERS_TEST")
    @Tag("app.monsoere")
    @Tag("MONSOERE")
    public Stream<DynamicNode> addApplicationMonsoreDynamic() throws Exception {
        MonSoereFixture monSoereFixture = new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);

        AtomicReference<String> appId = new AtomicReference<>();
        return Stream.of(
                dynamicContainer("initialisation des utilisateurs", Stream.of(dynamicTest("initialisation de l'utilisateur monsoresimple",
                                () -> {
                                    fixtures.monsoresimpleConnection = fixtures.createUserForUserDefinition(monsoresimple, true, false);
                                    assertThat(fixtures.getMonsoresimpleConnection()).extracting("userResult.login", "userResult.email", "userResult.accountState", "jwt")
                                            .satisfies(tuple -> {
                                                assertThat(tuple.get(0)).isEqualTo(monsoresimple.login());
                                                assertThat(tuple.get(1)).isEqualTo(monsoresimple.email());
                                                assertThat(tuple.get(2)).isEqualTo(OreSiUser.OreSiUserStates.active);
                                                assertThat(tuple.get(3)).isNotNull();
                                            });
                                }),
                        dynamicTest("initialisation de l'utilisateur withRightsUser", () -> {
                            fixtures.withRightsUserConnection = fixtures.createUserForUserDefinition(withRightsUser, true, false);
                            assertThat(fixtures.getWithRightsUserConnection()).extracting("userResult.login", "userResult.email", "userResult.accountState", "jwt").satisfies(tuple -> {
                                assertThat(tuple.get(0)).isEqualTo(withRightsUser.login());
                                assertThat(tuple.get(1)).isEqualTo(withRightsUser.email());
                                assertThat(tuple.get(2)).isEqualTo(OreSiUser.OreSiUserStates.active);
                                assertThat(tuple.get(3)).isNotNull();
                            });
                        }))),
                dynamicTest("test public", monSoereFixture::testPublic),
                dynamicContainer("chargement de MONSOERE",
                        monSoereFixture.loadMonsore(appId)),
                dynamicContainer("vérification des chargements et enregitrement des résultats", monSoereFixture.checkAndRegisterResults()));
    }

    @Test
    @Tag("OTHERS_TEST")
    @Tag("core.basic")
    public void buildSwaggerApi() throws Exception {
        mockMvc.perform(get("/v2/api-docs"));
    }

    @Test
    @Tag("OTHERS_TEST")
    @Tag("domain.model")
    public void testMultiplicityMany() throws Exception {
        final URL resource = getClass().getResource(Fixtures.getMultiplicityMany());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "multiplicity");

            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "multiplicity", ""));

            mockMvc.perform(get("/api/v1/applications/multiplicity", "ALL,ReferenceType")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getMultiplicityReferencesFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                final String response = mockMvc.perform(multipart("/api/v1/applications/multiplicity/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        final String reference1Data = mockMvc.perform(get("/api/v1/applications/multiplicity/data/reference1/json")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        final List<Map<String, Object>> reference1Rows = JsonPath.parse(reference1Data).read("$.rows");
        assertThat(reference1Rows).anySatisfy(row -> {
            final Map<String, Object> values = (Map<String, Object>) row.get("values");
            assertThat((List<Integer>) values.get("projets")).containsExactlyInAnyOrder(4, 5, 9);
            assertThat((List<String>) values.get("names")).containsExactlyInAnyOrder("toto1.1", "toto1.2", "toto1.3");
            assertThat((List<Double>) values.get("durations")).containsExactlyInAnyOrder(-4.5, 5.6, 3.2);
            assertThat((List<String>) values.get("dates")).containsExactlyInAnyOrder("date:2014-01-20T00:00:00:dd/MM/yyyy", "date:2014-06-23T00:00:00:dd/MM/yyyy");
        });
        mockMvc.perform(get("/api/v1/applications/multiplicity/data/reference2/json")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows[*].values.reference1[*]", hasItems("toto__toto1", "toto__toto2", "tutu__tutu1", "tutu__tutu2")));
        try (final InputStream refStream = getClass().getResourceAsStream(Fixtures.getMultiplicityManyData())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "bugs.csv", "text/plain", refStream);

            mockMvc.perform(get("/api/v1/applications/multiplicity/data/bugs/json")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$.referenceTypeForReferencingColumns.reference", is("reference1"))) // TODO
                    //.andExpect(jsonPath("$.referenceTypeForReferencingColumns.references", is("reference1"))) // TODO
                    .andExpect(jsonPath("$.rows[0].values.dates", hasItems("date:2002-01-23T00:00:00:dd/MM/yyyy", "date:2002-01-24T00:00:00:dd/MM/yyyy"))).andExpect(jsonPath("$.rows[0].values.projets", hasItems(1, 2))).andExpect(jsonPath("$.rows[0].values.fichiers", hasItems("file1", "file2"))).andExpect(jsonPath("$.rows[0].values.durations", hasItems(3.2, 5.4))).andExpect(jsonPath("$.rows[0].values.references", hasItems("toto__toto1", "tutu__tutu1"))).andReturn().getResponse().getContentAsString();
        }
    }

    @TestFactory
    @Tag("core.config")
    public Stream<DynamicNode> addApplicationWithComputedComponentsWithReferences() {
        MinotaurFixture minotaurFixture = new MinotaurFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicContainer("Chargement de l'application Minotaur", minotaurFixture.loadApplication()),
                dynamicContainer("Chargement des référentiels Minotaur", minotaurFixture.loadReferences()),
                dynamicContainer("Chargement des données Minotaur", minotaurFixture.loadData()),
                dynamicContainer("Vérification des résultats Minotaur", minotaurFixture.checkResults())
        );
    }

    private String loadApplicationMonsoere(InputStream in) throws Throwable {
        final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
        //définition de l'application
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "monsore");
        final MvcResult resultForValidateMonsore = fixtures.validateApplication(configuration, fixtures.adminConnection.jwt());
        List<ReactiveTypeResult> results = Fixtures.getResults(resultForValidateMonsore);
        Assertions.assertTrue(results.stream().noneMatch(obj -> false));
        final String responseForTestingmonsoere = resultForValidateMonsore.getResponse().getContentAsString();
        final MvcResult resultForCreateMonsore = fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "monsore", "");
        registerFile("ui/cypress/fixtures/applications/ore/monsore/validateMonsore.txt", responseForTestingmonsoere);

        return resultForCreateMonsore.getResponse().getContentAsString();
    }

    @TestFactory
    @Tag("OTHERS_TEST")
    @Tag("app.monsoere")
    @Tag("MONSOERE")
    @Tag("GENERATE_CYPRESS_FIXTURES")
    public Stream<DynamicNode> addApplicationMonsoreWithRepositoryDynamic() throws Exception {
        AtomicReference<String> oirFilesUUID = new AtomicReference<>();
        AtomicReference<String> fileUUID2 = new AtomicReference<>();
        List<String> ids = new LinkedList<>();
        List<String> hierarchicalKeys = new LinkedList<>();

        return Stream.of(
                dynamicContainer("Initialisation et Configuration", Stream.of(
                        dynamicTest("Création utilisateur avec droits", () -> {
                            fixtures.withRightsUserConnection = fixtures.createUserForUserDefinition(withRightsUser, true, false);
                        }),
                        dynamicTest("Chargement configuration V1 et mise à jour V2", () -> {
                            URL resource = getClass().getResource(getMonsoreApplicationConfigurationWithRepositoryResourceName());
                            try (final InputStream in = Objects.requireNonNull(resource).openStream();
                                 final InputStream inV2 = changeToV2(Objects.requireNonNull(resource).openStream())) {
                                final String responseForCreatemonsoere = loadApplicationMonsoere(in);

                                final MockMultipartFile configurationV2 = new MockMultipartFile("file", "monsore.yaml", "text/plain", inV2);
                                final MvcResult resultForChangeMonsore = fixtures.changeConfiguration(configurationV2, fixtures.adminConnection.jwt(), "monsore", "monsorev2");
                                final String responseForChangemonsoere = resultForChangeMonsore.getResponse().getContentAsString();

                                registerFile("ui/cypress/fixtures/applications/ore/monsore/createMonsore.txt", responseForCreatemonsoere);
                                registerFile("ui/cypress/fixtures/applications/ore/monsore/changeMonsore.txt", responseForChangemonsoere);

                                Assertions.assertTrue(Arrays.stream(getApplicationsFlux(fixtures.adminConnection.jwt(), "ALL"))
                                        .anyMatch(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class))
                                                       && ((List<String>) JsonPath.parse(s).read("$.result.application.data")).contains("sites")
                                                       && !((List<String>) JsonPath.parse(s).read("$.result.application.data")).contains("type de fichiers")));

                                mockMvc.perform(get("/api/v1/applications/monsore")
                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt())
                                        .param("filter", "ALL")).andExpect(status().is2xxSuccessful());
                            }
                        })
                )),

                dynamicContainer("Gestion des Droits", Stream.of(
                        dynamicTest("Création demande de droits", () -> {
                            final String rightsRequest = """
                                    {
                                      "id": "",
                                      "comment": "Un commentaire",
                                      "setted": false,
                                      "fields": {
                                        "organization": "INRAE",
                                        "project": "openAdom",
                                        "startDate": "10/10/1010",
                                        "projectManagers": "toto,titi"
                                      },
                                      "rightsRequest": {
                                        "usersId": null,
                                        "applicationNameOrId": "monsore",
                                        "id": null,
                                        "name": "une submissionScope sur monsore",
                                        "dataName": "pem",
                                        "authorizations": {
                                          "pem": {
                                            "extraction": [
                                              {
                                                "requiredAuthorizations": {
                                                  "projet": "projetKprojet_manche",
                                                  "localization": "plateforme.nivelle.nivelle__p1"
                                                },
                                                "timeScope": {
                                                  "fromDay": [1984, 1, 1],
                                                  "toDay": [1984, 1, 6]
                                                }
                                              }
                                            ]
                                          }
                                        }
                                      }
                                    }
                                    """;

                            mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest").contentType(MediaType.APPLICATION_JSON).content(rightsRequest)
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            ).andExpect(status().is2xxSuccessful());

                            mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest").contentType(MediaType.APPLICATION_JSON).content(rightsRequest)
                                    .header("Authorization", "Bearer " + fixtures.lambdaConnection.jwt()))
                            ).andExpect(status().is2xxSuccessful());
                        }),
                        dynamicTest("Vérification demande de droits", () -> {
                            final String json = """
                                    {
                                      "uuids": [],
                                      "authorizations": [],
                                      "locale": "fr_FR",
                                      "offset": 0,
                                      "limit": 1,
                                      "fieldFilters": [
                                        {
                                          "field": "organization",
                                          "filter": "INRAE",
                                          "type": null,
                                          "format": null,
                                          "intervalValues": null,
                                          "isRegExp": null
                                        }
                                      ]
                                    }""";

                            mockMvc.perform((get("/api/v1/applications/monsore/rightsRequest").contentType(MediaType.APPLICATION_JSON).param("params", json)
                                    .header("Authorization", "Bearer " + fixtures.lambdaConnection.jwt()))
                            ).andExpect(status().is2xxSuccessful());
                        })
                )),

                dynamicContainer("Référentiels et Permissions", Stream.of(
                        dynamicTest("Echec upload sans droits", () -> {
                            String typeDeSites = getMonsoreReferentielFiles().get("type_de_sites");
                            String sites = getMonsoreReferentielFiles().get("sites");

                            try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);
                                Assertions.assertInstanceOf(NotApplicationDataWriterException.class, mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andExpect(status().is4xxClientError()).andReturn().getResolvedException());
                            }
                            try (final InputStream refStream = getClass().getResourceAsStream(sites)) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", sites, "text/plain", refStream);
                                Assertions.assertInstanceOf(NotApplicationDataWriterException.class, mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "sites").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andExpect(status().is4xxClientError()).andReturn().getResolvedException());
                            }
                        }),
                        dynamicTest("Attribution droits et vérification", () -> {
                            getJsonRightForAll(fixtures.getWithRightsUserConnection().userResult().userId().toString(), List.of(List.of("sites", "publication"), List.of("type_de_sites", "publication")));

                            mockMvc.perform(get("/api/v1/applications/monsore/authorization/user/{userId}", fixtures.getWithRightsUserConnection().userResult().userId().toString())
                                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(status().is2xxSuccessful())
                                    .andExpect(jsonPath("$.userAuthorization.type_de_sites[0].operationTypes", hasItems("publication", "depot", "extraction")))
                                    .andExpect(jsonPath("$.userAuthorization.sites[0].operationTypes", hasItems("publication", "depot", "extraction")));
                        }),
                        dynamicTest("Upload référentiels avec droits", () -> {
                            String typeDeSites = getMonsoreReferentielFiles().get("type_de_sites");
                            try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue()));
                            }
                            // Upload une deuxième fois (mise à jour)
                            try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue()));
                            }
                        })
                )),

                dynamicContainer("Consultation et Suppression Données", Stream.of(
                        dynamicTest("Vérification clés naturelles et hiérarchiques", () -> {
                            mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites")
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andDo(result -> {
                                        String contentAsString = result.getResponse().getContentAsString();
                                        String[] read1 = JsonPath.parse(contentAsString).read("$.rows[*].naturalKey", String[].class);
                                        List<String> naturalKeys = new LinkedList<>(Arrays.asList(read1));
                                        CollectionUtils.isEqualCollection(naturalKeys, List.of("bassin_versant", "plateforme"));

                                        read1 = JsonPath.parse(contentAsString).read("$.rows[*].hierarchicalKey", String[].class);
                                        hierarchicalKeys.addAll(Arrays.asList(read1));
                                        CollectionUtils.isEqualCollection(hierarchicalKeys, List.of("type_de_sitesKbassin_versant", "type_de_sitesKplateforme"));

                                        read1 = JsonPath.parse(contentAsString).read("$.rows[*].rowId[*]", String[].class);
                                        ids.addAll(Arrays.asList(read1));
                                        Assertions.assertEquals(2, read1.length);
                                    });
                        }),
                        dynamicTest("Recherche sur critère", () -> {
                            String response = mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites").locale(Locale.FRENCH).param("tze_nom_en", "Platform").param("downloadDatasetQuery", """
                                                    {
                                                           "componentFilters": [
                                                                     {
                                                                       "componentKey": "tze_nom_en",
                                                                       "filters": ["Platform", "titi"]
                                                                     }
                                                          ]
                                                        }""")
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(jsonPath("$.rows.length()", equalTo(1))).andExpect(jsonPath("$.rows[0].values.tze_nom_fr", equalTo("Plateforme"))).andReturn().getResponse().getContentAsString();
                            Assertions.assertFalse(response.contains("tze_nom_en"));
                        }),
                        dynamicTest("Recherche par ID", () -> {
                            mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites").param("downloadDatasetQuery", SELECT_ROW_BY_ID.formatted(ids.get(1)))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(jsonPath("$.rows[0].rowId[0]", equalTo(ids.get(1))));
                        }),
                        dynamicTest("Test suppression (stratégie INSERTING)", () -> {
                            // Nous sommes dans une strategie OA_INSERTING-> delete non compris dans le get
                            mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites").param("downloadDatasetQuery", SELECT_ROW_BY_NATURAL_KEY.formatted(hierarchicalKeys.get(1)))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(jsonPath("$.rows[0].hierarchicalKey", equalTo(hierarchicalKeys.get(1))));

                            String deletedIds = mockMvc.perform(delete("/api/v1/applications/monsore/data/{data}", "type_de_sites")
                                            .param("downloadDatasetQuery", SELECT_ROW_BY_ID.formatted(ids.get(1)))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andReturn().getResponse().getContentAsString();
                            Assertions.assertFalse(deletedIds.contains(ids.get(1)));

                            //suppression par id
                            mockMvc.perform(delete("/api/v1/applications/monsore/data/{refType}", "type_de_sites").param("_row_id_", ids.get(1))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andReturn().getResponse().getContentAsString();
                        })
                )),

                dynamicContainer("Chargement autres référentiels", Stream.of(
                        dynamicTest("Upload autres fichiers", () -> {
                            for (final Map.Entry<String, String> e : getMonsoreReferentielFiles().entrySet()) {
                                if ("pem".equals(e.getKey())) {
                                    continue;
                                }
                                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                                    mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", e.getKey()).file(refFile)
                                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                            .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue()));
                                }
                            }
                        })
                )),

                dynamicContainer("Fichiers Additionnels", Stream.of(
                        dynamicTest("Upload et vérification", () -> {
                            URL resource = getClass().getResource(getMonsoreApplicationConfigurationWithRepositoryResourceName());
                            try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
                                final MockMultipartFile addFile = new MockMultipartFile("file", "monsoere.yaml", "text/plain", in);
                                final String json = """
                                        {
                                          "id": "",
                                          "comment": "un fichier déposé",
                                          "fileType": "fichiers",
                                          "fields": {
                                            "age": "10",
                                            "nom": "dix",
                                            "date": "10/10/1010",
                                            "site": "oir",
                                            "poids": "10.10"
                                          },
                                          "pem": {
                                                "operationTypes": ["associate"],
                                                "requiredAuthorizations": {
                                                      "projet":  ["projet_atlantique", "projetKprojet_manche"]
                                                    }
                                          }
                                        }""";
                                mockMvc.perform((multipart("/api/v1/applications/monsore/additionalFiles/fichiers").file(addFile).param("params", json)
                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                ).andExpect(status().is2xxSuccessful());

                                mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(jsonPath("$.users[*].label", contains("_public_", "lambda", "poussin", "withrigths")))
                                        .andExpect(jsonPath("$.additionalFileName", is("fichiers")))
                                        .andExpect(jsonPath("$.additionalBinaryFiles[0].additionalBinaryFileForm.age", is("10")));
                            }
                        }),
                        dynamicTest("Vérification accès et zip", () -> {
                            final String additionalJsonRequest = """
                                    {
                                      "uuids": [],
                                      "additionalFilesInfos": {
                                        "fichiers": {
                                          "fieldFilters": [
                                            {
                                              "field": "nom",
                                              "filter": "dix",
                                              "type": "",
                                              "format": null,
                                              "intervalValues": null,
                                              "isRegExp": false
                                            }
                                          ]
                                        }
                                      },
                                      "locale": "fr_FR",
                                      "offset": 0,
                                      "limit": null
                                    }""";

                            // Test accès refusé
                            final String error = Objects.requireNonNull(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                                            .header("Authorization", "Bearer " + fixtures.lambdaConnection.jwt()))
                                    .andExpect(status().is4xxClientError()).andReturn().getResolvedException()).getMessage();
                            Assertions.assertEquals("application inconnue 'monsore'", error);

                            // Test zip avec droits
                            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles").param("nameOrId", "monsore").param("params", additionalJsonRequest).accept(MediaType.APPLICATION_OCTET_STREAM)
                                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(status().is2xxSuccessful()).andExpect(request().asyncStarted()).andReturn())).andExpect(result -> {
                                final List<ZipEntry> entries = new ArrayList<>();
                                try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                                    ZipEntry zipEntry;
                                    while ((zipEntry = zi.getNextEntry()) != null) {
                                        entries.add(zipEntry);
                                    }
                                }
                                List<String> entryNames = entries.stream().map(ZipEntry::getName).toList();
                                Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere_infos.txt"));
                                Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere.yaml"));
                            });
                        })
                )),

                dynamicContainer("Cycle de vie Données (PEM)", Stream.of(
                        dynamicTest("Vérification données vides", () -> {
                            mockMvc.perform(get("/api/v1/applications/monsore/data/{refType}/json", "type_de_fichiers")
                                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(jsonPath("$.rows", hasSize(0)));
                        }),
                        dynamicTest("Echec dépôt sans droits", () -> {
                            final String projet = "manche";
                            final String plateforme = "plateforme";
                            final String site = "NULL_KEY__oir";
                            URL resource = getClass().getResource(getPemRepositoryDataResourceName(projet, site));
                            try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
                                try {
                                    mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem").file(refFile).param("params", getPemRepositoryParams(projet, plateforme, site, false))
                                                    .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                            .andExpect(status().is4xxClientError())
                                            .andExpect(jsonPath("$.message", Is.is(NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER)));
                                } catch (ServletException servletException) {
                                    SiOreAuthorizationRequestException cause = (SiOreAuthorizationRequestException) servletException.getCause();
                                    AuthorizationRequestException requestException = cause.getException();
                                    Assertions.assertEquals(AuthorizationRequestException.MISSING_REQUIRED_AUTHORIZATION, requestException);
                                }
                            }
                        }),
                        dynamicTest("Dépôt avec droits et versioning", () -> {
                            final String projet = "manche";
                            final String plateforme = "plateforme";
                            final String site = "NULL_KEY__oir";

                            // Attribution droits
                            getJsonRightsforRestrictions(fixtures.getWithRightsUserConnection().userResult().userId().toString(), List.of(OperationType.depot.name()), "monsore", "pem", "type_de_sitesKplateforme.sitesKNULL_KEY__oir.sitesKNULL_KEY__oir__p1", "01/01/1984", "06/01/1984", fixtures.adminConnection.jwt());

                            URL resource = getClass().getResource(getPemRepositoryDataResourceName(projet, site));
                            try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
                                for (int i = 0; i < 3; i++) {
                                    mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                                                    .file(refFile)
                                                    .param("params", getPemRepositoryParams(projet, plateforme, site, false))
                                                    .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                            .andExpect(status().is2xxSuccessful());
                                }
                            }

                            // Vérification versions
                            String response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                                            .param("repositoryId", getPemRepositoryId(plateforme, projet, site))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(status().is2xxSuccessful())
                                    .andExpect(jsonPath("$", hasSize(3)))
                                    .andExpect(jsonPath("$[*][?(@.params.published == false )]", hasSize(3)))
                                    .andReturn().getResponse().getContentAsString();

                            oirFilesUUID.set(JsonPath.parse(response).read("$[2].id"));
                        }),
                        // Test supprimé : Vérification métriques nécessite Prometheus/Grafana lancés (impossible en CI/CD)
                        dynamicTest("Publication et vérification données", () -> {
                            final String projet = "manche";
                            final String plateforme = "plateforme";
                            final String site = "NULL_KEY__oir";

                            // Publication
                            mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem").param("params", Fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID.get(), true))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(status().is2xxSuccessful());

                            // Vérification statut publié
                            mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem").param("repositoryId", getPemRepositoryId(plateforme, projet, site))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(status().is2xxSuccessful())
                                    .andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(1)))
                                    .andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(oirFilesUUID.get()));

                            // Vérification données JSON
                            mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(status().is2xxSuccessful())
                                    .andExpect(jsonPath("$.rows", hasSize(34)))
                                    .andExpect(jsonPath("$.rows[*].values[? (@.chemin == 'NULL_KEY__oir__p1' && @.projet == 'projet_manche')]", hasSize(34)));

                            // Vérification ZIP
                            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/data/pem/zip").accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                            .andExpect(status().is2xxSuccessful()).andExpect(request().asyncStarted()).andReturn()))
                                    .andExpect(testZip(List.of("pem.csv", "references/especes.csv", "references/type_de_sites.csv", "references/unites.csv", "references/projet.csv", "references/valeurs_qualitatives.csv", "references/sites.csv")));
                        }),
                        dynamicTest("Publication multiple et suppression", () -> {
                            publishOrDepublish(fixtures.adminConnection.jwt(), "manche", "plateforme", "NULL_KEY__scarff", 68, true, 1, true);
                            publishOrDepublish(fixtures.adminConnection.jwt(), "atlantique", "plateforme", "NULL_KEY__scarff", 34, true, 1, true);
                            publishOrDepublish(fixtures.adminConnection.jwt(), "atlantique", "plateforme", "NULL_KEY__nivelle", 34, true, 1, true);
                            publishOrDepublish(fixtures.adminConnection.jwt(), "manche", "plateforme", "NULL_KEY__nivelle", 34, true, 1, true);

                            final String fileUUID = publishOrDepublish(fixtures.adminConnection.jwt(), "manche", "plateforme", "NULL_KEY__nivelle", 34, true, 2, true);

                            // Suppression
                            String response = mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID)
                                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
                            Assertions.assertEquals(response, fileUUID);
                        }),
                        dynamicTest("Droits publication/dépôt et suppression utilisateur", () -> {
                            try {
                                publishOrDepublish(fixtures.getWithRightsUserConnection().jwt(), "manche", "plateforme", "NULL_KEY__nivelle", 34, true, 1, true);
                            } catch (final NotApplicationDataWriterForDepositException e) {
                                Assertions.assertEquals(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT, e.getMessage());
                            }

                            getJsonRightsforRestrictions(fixtures.getWithRightsUserConnection().userResult().userId().toString(), List.of(OperationType.publication.name()), "monsore", "pem", "type_de_sitesKplateforme.sitesKNULL_KEY__nivelle.sitesKNULL_KEY__nivelle__p1", "01/01/1984", "06/01/1984", fixtures.adminConnection.jwt());

                            fileUUID2.set(publishOrDepublish(fixtures.getWithRightsUserConnection().jwt(), "manche", "plateforme", "NULL_KEY__nivelle", 34, true, 2, true));
                            testFilesAndDataOnServer("plateforme", "manche", "NULL_KEY__nivelle", 0, 2, fileUUID2.get(), true);

                            // Dépublication
                            mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem").param("params", Fixtures.getPemRepositoryParamsWithId("manche", "plateforme", "NULL_KEY__oir", oirFilesUUID.get(), false))
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(status().is2xxSuccessful());

                            // Suppression finale
                            mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID2.get())
                                            .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                    .andExpect(status().is2xxSuccessful());
                        })
                ))
                /*
        // TODO: Réactiver ces tests après refactoring de la suppression PEM
        ,
        dynamicContainer("Tests à réactiver", Stream.of(
            dynamicTest("delete pem", () -> {
                final String filterPattern = """
                {
                  "offset": null,
                  "limit": 10,
                  "componentSelects": [],
                  "componentFilters": [
                    {
                      "componentKey": {
                        "variable": "Nombre d'individus",
                        "component": "value"
                      },
                      "filter": %1$s,
                      "type": "numeric",
                      "format": "integer",
                      "intervalValues": %2$s,
                      "isRegExp": null
                    }
                  ],
                  "componentOrderBy": [],
                  "authorizationDescriptions": []
                }
                """;
                final String filter = filterPattern.formatted(null, "{\"from\":\"15\",\"to\":\"15\"}");
                mockMvc.perform(delete("/api/v1/applications/monsoresimple/data/pem")
                    .param("downloadDatasetQuery", filter)
                    .cookie(monsoresimpleConnection.jwt()))
                    .andExpect(status().is2xxSuccessful())
                    .andDo(result -> {
                        String[] uuids = result.getResponse().getContentAsString().split(",");
                        final int expectedUUIDs = 24;
                        Assertions.assertEquals(expectedUUIDs, uuids.length,
                            String.format("On attend %d lignes; la requête en renvoie %d", expectedUUIDs, uuvels.length));
                    });
            }),

            dynamicTest("authorizations", () ->
                mockMvc.perform(get("/api/v1/applications/monsoresimple/authorization")
                    .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection()))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString()
            ),

            dynamicTest("grantables", () ->
                mockMvc.perform(get("/api/v1/applications/monsoresimple/grantable")
                    .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection()))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString()
            )
        ))
        */

        );
    }


    private String[] getApplicationsFlux(final String jwt, final String... filter) throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications").accept(MediaType.APPLICATION_NDJSON_VALUE)
                .header("Authorization", "Bearer " + jwt)
                .param("filter", filter)).andExpect(status().is2xxSuccessful()).andExpect(request().asyncStarted()).andReturn())).andReturn().getResponse().getContentAsString().split("\n");
    }

    private String getJsonRightsforRestrictions(final String withRigthsUserId, final List<String> roles, final String applicationName, final String datatype, final String localization, final String from, final String to, String jwt) throws Exception {
        jwt = jwt == null ? fixtures.adminConnection.jwt() : jwt;
        String json = String.format("""
                {
                   "usersId":["%6$s"],
                   "uuid": null,
                   "name": "une submissionScope sur monsore%7$s",
                   "description": "une description de submissionScope sur monsore",
                   "authorizationsWithRestriction":{
                        "%1$s":{
                               "operationTypes": ["%5$s"],
                               "requiredAuthorizations":{
                                     "projet":["projetKprojet_manche"],
                                     "sites":["%2$s"]
                                 },
                                "timeScope":{
                                   "format": "dd/MM/yyyy",
                                   "fromDay": "%3$s",
                                   "toDay": "%4$s"
                                }
                           }
                      }
                }""", datatype, localization, from, to, String.join("\",\"", roles), withRigthsUserId, System.currentTimeMillis());
        MockHttpServletRequestBuilder createRight = post("/api/v1/applications/%s/authorization".formatted(applicationName)).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content(json);
        return mockMvc.perform(createRight).andDo(result -> {
            final int status = result.getResponse().getStatus();
            if (status > 300) {
                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
            }
        }).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }


    private String getJsonRightForAll(final String withRigthsUserId, List<List<String>> dataNameAndRoles) throws Exception {
        List<String> formattedStrings = dataNameAndRoles.stream().filter(subList -> !subList.isEmpty()).map(subList -> {
            String dataname = subList.getFirst();
            List<String> roles = subList.subList(1, subList.size());
            String rolesString = roles.stream().collect(Collectors.joining("\" ,\"", "\"", "\""));
            return String.format("\"%s\": [%s]", dataname, rolesString);
        }).toList();

        String authorizationForAll = String.join(",\n", formattedStrings);
        String json = String.format("""
                {
                  "usersId": [
                    "%1$s"
                  ],
                  "uuid": null,
                  "name": "une submissionScope sur le référentiel monsore",
                  "description": "une description de submissionScope sur le référentiel monsore",
                  "authorizationForAll": {
                    %2$s
                  }
                }""", withRigthsUserId, authorizationForAll);
        MockMultipartHttpServletRequestBuilder createRight = multipart("/api/v1/applications/monsore/authorization").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt())
                .content(json);
        return mockMvc.perform(createRight).andDo(result -> {
            final int status = result.getResponse().getStatus();
            if (status > 300) {
                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
            }
        }).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }


    private String publishOrDepublish(final String jwt, final String projet, final String plateforme, final String site, final int expected, final boolean toPublish, final int numberOfVersions, final boolean published) throws Exception {
        final URL resource;
        resource = getClass().getResource(getPemRepositoryDataResourceName(projet, site));
        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
            refFile.transferTo(Path.of("/tmp/pem.csv"));
            MvcResult mockResponse = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem").file(refFile).param("params", getPemRepositoryParams(projet, plateforme, site, toPublish))
                            .header("Authorization", "Bearer " + jwt))
                    .andReturn();
            if (mockResponse.getResponse().getStatus() >= 200 && mockResponse.getResponse().getStatus() < 300) {
                final String fileUUID = JsonPath.parse(mockResponse.getResponse().getContentAsString()).read("$.id");
                testFilesAndDataOnServer(plateforme, projet, site, expected, numberOfVersions, fileUUID, published);
                return fileUUID;
            }
            throw Objects.requireNonNull(mockResponse.getResolvedException());
        }
    }

    @TestFactory
    @Tag("core.config")
    @SuppressWarnings("java:S2699") // assertions dans les DynamicNode — non détectées par SonarQube
    public Stream<DynamicNode> testProgressiveYamlWithoutAuthorization() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicContainer("Chargement de l'application", progressiveFixture.loadApplication("yamlWithoutAuthorization")),
                dynamicContainer("Chargement des référentiels", progressiveFixture.loadReferences()),
                dynamicContainer("Chargement des données", progressiveFixture.loadData())
        );
    }

    @TestFactory
    @Tag("core.config")
    @SuppressWarnings("java:S2699") // assertions dans les DynamicNode — non détectées par SonarQube
    public Stream<DynamicNode> testProgressiveYamlWithEmptyDatagroup() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicContainer("Chargement de l'application", progressiveFixture.loadApplication("yamlWithEmptyDatagroup")),
                dynamicContainer("Chargement des référentiels", progressiveFixture.loadReferences()),
                dynamicContainer("Chargement des données", progressiveFixture.loadData())
        );
    }

    @TestFactory
    @Tag("core.config")
    public Stream<DynamicNode> testProgressiveYamlWithNoReference() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicTest("Chargement de l'application", () -> {
                    try {
                        progressiveFixture.loadApplication("testAuthorizationScopeWithoutReference").forEach(DynamicTest::getExecutable);
                    } catch (OreSiTechnicalException e) {
                        Assertions.assertTrue(e.getCause() instanceof AssertionFailedError);
                        Assertions.assertTrue(e.getCause().getMessage().contains("invalidComponentReferenceForAuthorizationScopeComponentName"));
                    }
                })
        );
    }

    @TestFactory
    @Tag("core.config")
    @SuppressWarnings("java:S2699") // assertions dans les DynamicNode — non détectées par SonarQube
    public Stream<DynamicNode> testProgressiveYamlWithoutAuthorizationScope() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicContainer("Chargement de l'application", progressiveFixture.loadApplication("testProgressiveYamlWithoutAuthorizationScope")),
                dynamicContainer("Chargement des référentiels", progressiveFixture.loadReferences()),
                dynamicContainer("Chargement des données", progressiveFixture.loadData())
        );
    }

    @TestFactory
    @Tag("core.config")
    @SuppressWarnings("java:S2699") // assertions dans les DynamicNode — non détectées par SonarQube
    public Stream<DynamicNode> testProgressiveYamlWithoutTimescopeScope() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicContainer("Chargement de l'application", progressiveFixture.loadApplication("testProgressiveYamlWithoutTimescopeScope")),
                dynamicContainer("Chargement des référentiels", progressiveFixture.loadReferences()),
                dynamicContainer("Chargement des données", progressiveFixture.loadData())
        );
    }

    @TestFactory
    @Tag("core.config")
    public Stream<DynamicNode> testProgressiveWithReferenceAndNoHierarchicalReferenceYaml() {
        ProgressiveFixture progressiveFixture = new ProgressiveFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicTest("Chargement de l'application", () -> {
                    try {
                        progressiveFixture.loadApplication("testAuthorizationScopeWithReferenceAndNoHierarchicalReference").forEach(DynamicTest::getExecutable);
                    } catch (OreSiTechnicalException e) {
                        Assertions.assertTrue(e.getCause() instanceof AssertionFailedError);
                        Assertions.assertTrue(e.getCause().getMessage().contains("invalidComponentReferenceForAuthorizationScopeComponentName"));
                    }
                })
        );
    }

    private void progressiveYamlAddReferences() throws Exception {
        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getProgressiveYamlReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    private void progressiveYamlAddData() throws Exception {
        String response;
        for (final Map.Entry<String, String> e : Fixtures.getProgressiveYamlDataFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.fileId", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();
            }
        }
    }

    @Test
    @Tag("SUITE")
    @Tag("app.recursivity")
    public void testRecursivity() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getRecursivityApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "recursivite");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "recursivite", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/recursivite").param("filter", "ALL")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.type", IsEqual.equalTo("DynamicComponent"))).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon"))).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_"))).andExpect(jsonPath("$.internationalization.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Taxa properties"))).andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/recursivite/data/{refType}/json", "taxon")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
/*                    .andExpect(jsonPath("$..values.tel_S2_value", contains("7.2", "3.4", "2.1", "2.6", "2.5", "5.2", "3.9", "3.2", "1.2")))
                    .andExpect(jsonPath("$..values.tel_S2_resolution", contains(3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2)))
                    .andExpect(jsonPath("$..values.tel_S2_qualifier", contains(3, 3, 3, 3, 3, 3, 3, 3, 3)))
                    .andExpect(jsonPath("$..values.tel_S2_variable", contains("annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy")))
                    .andExpect(jsonPath("$.referenceTypeForReferencingColumns.tel_S2_variable", Is.is("site")));*/
            //.andReturn().getResponse().getContentAsString();

        }
        // Ajout de taxon
        {
            for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielTaxon().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile)
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        }
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    @Test
    @Tag("SUITE")
    @Tag("app.pattern")
    public void testPattern() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getPatternApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "pattern.yaml", "text/plain", in);
            //définition de l'application
            fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "pattern");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "pattern", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/pattern").param("filter", "ALL")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    }).andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.type", IsEqual.equalTo("DynamicComponent"))).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon"))).andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_"))).andExpect(jsonPath("$.internationalization.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Properties of Taxa"))).andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/pattern/data/{refType}/json", "taxon")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$..values.tel_S2_value[*].__VALUE__", containsInAnyOrder("7.2", "3.4", "2.1", "2.6", "2.5", "5.2", "3.9", "3.2", "1.2")))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_resolution", containsInAnyOrder(3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_qualifier", containsInAnyOrder(3, 3, 3, 3, 3, 3, 3, 3, 3)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_variable", containsInAnyOrder("annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy")))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].swc_qc", containsInAnyOrder(1, 1, 0, 2, 1, 0, 0, 1, 1)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].swc_sd", containsInAnyOrder(3.9, 2.5, 7.2, 3.2, 2.1, 3.4, 1.2, 5.2, 3.9)))
                    .andExpect(jsonPath("$.rows[*].refsLinkeds[?(     @.referenceType == 'proprietes_taxon'      && @.naturalKey.sql == 'niveau_incertitude_de_determination' )][? (@.naturalKey.sql=='niveau_incertitude_de_determination')].length()", containsInAnyOrder(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10)))
                    .andExpect(jsonPath("$.rows[*].refsLinkeds[?(@.referenceType == 'site')].naturalKey.sql", containsInAnyOrder("aiguebelette", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy", "annecy", "aiguebelette", "annecy", "annecy")))
                    .andReturn().getResponse().getContentAsString();

        }
        {
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/pattern/data/taxon/zip").accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            .andExpect(status().is2xxSuccessful()).andExpect(request().asyncStarted()).andReturn()))


                    .andExpect(testZip(java.util.List.of("taxon.csv", "references/proprietes_taxon.csv", "references/site.csv")));

        }
        // Ajout de taxon
        {
            for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey()).file(refFile)
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        }
        for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    //@Test
//@Disabled
//@Tag("SUITE")
//@Tag("core.config")
    public void testComputedWithNaturalKeyColumns() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getComputedWithNaturalKeyColumns());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "computedWithNaturalKeyColumns.yaml", "text/plain", in);
            //définition de l'application
            fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "computedwithnaturalkeycolumns");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "computedwithnaturalkeycolumns", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/computedwithnaturalkeycolumns").param("filter", "ALL")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getDataComputedWithNaturalKeyColumns().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}/json", "site_sit")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows[*].values.site_natural_key", contains("type_de_site1__site1", "type_de_site1__site2", "type_de_site2__site1", "type_de_site2__site2"))).andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4))).andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1"))).andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2"))).andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains(""))).andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains(""))).andExpect(jsonPath("$.rows[0].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1))).andExpect(jsonPath("$.rows[0].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1))).andExpect(jsonPath("$.rows[1].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1))).andExpect(jsonPath("$.rows[1].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1))).andExpect(jsonPath("$.rows[2].refsLinkedTo.site_sit.keys()", hasSize(1))).andExpect(jsonPath("$.rows[2].refsLinkedTo.type_site_tsi.keys()", hasSize(1))).andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4))).andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1"))).andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2"))).andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains(""))).andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains("")))
                    //.andExpect(jsonPath("$.rows[0].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[0].refsLinkedTo.type_site_tsi.tsi_noms", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[1].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[1].refsLinkedTo.type_site_tsi.tsi_noms", hasSize(2))) //TODO
                    .andExpect(jsonPath("$.rows[2].refsLinkedTo.site_sit.keys()", hasSize(1))).andExpect(jsonPath("$.rows[2].refsLinkedTo.type_site_tsi.keys()", hasSize(1)));
        }
    }

    private void testFilesAndDataOnServer(final String plateforme, final String projet, final String site, final int expected, final int numberOfVersions, final String fileUUID, final boolean published) throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem").param("repositoryId", getPemRepositoryId(plateforme, projet, site))
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$", hasSize(numberOfVersions)));

        if (published) {
            resultActions = resultActions.andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(1))).andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(fileUUID));
        } else {
            resultActions = resultActions.andExpect(jsonPath("$[*][?(@.params.published == true )]").isEmpty());
        }
        resultActions.andReturn().getResponse().getContentAsString();
    }

    @TestFactory
    @DisplayName("Tests de l'application ACBB")
    @Tag("app.acbb")
    Stream<DynamicNode> addApplicationAcbb() {
        AcbbFixture acbbFixture = new AcbbFixture(fixtures, mockMvc);
        return Stream.of(
                dynamicTest("init users and rights", () -> fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "acbb")), dynamicTest("load acbb", () -> {
                    final URL resource = getClass().getResource(AcbbFixture.getAcbbApplicationConfigurationResourceName());
                    assert resource != null;
                    try (final InputStream in = resource.openStream()) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", in);
                        final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "acbb_openadom_v2", ""));
                    } catch (final Throwable e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    }
                }),
                dynamicContainer("load acbb References", acbbFixture.loadAcbbReferences()), dynamicContainer("add data SWC", Stream.of(dynamicTest("load swc", () -> {
                    try (final InputStream in = getClass().getResourceAsStream(AcbbFixture.getFluxToursDataResourceName())) {
                        final MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);

                        final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx").file(file).param("params", """
                                                {
                                                    "fileid":null,
                                                    "binaryfiledataset":{
                                                        "datatype":"t_flux_tours_flx",
                                                        "requiredAuthorizations":{
                                                           "tr_sites_sit":["laqueuille"]
                                                        },
                                                        "from":"2003-12-31 23:00:00",
                                                        "to":"2004-12-31 23:00:00",
                                                        "comment":null
                                                    },
                                                    "topublish":true}"""

                                        )
                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                .andDo(result -> {
                                    final int status = result.getResponse().getStatus();
                                    if (status > 300) {
                                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                    }
                                }).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();


                    }
                }), dynamicTest("read SWC to json", () -> {
//            String expectedJson = Resources.toString(getClass().getResource("/data/acbb_openadom_v2/compare/export.json"), StandardCharsets.UTF_8);
                    mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/json")
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt())
                                    .accept(MediaType.APPLICATION_JSON)).andDo(result -> {
                                final int status = result.getResponse().getStatus();
                                if (status > 300) {
                                    System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                }
                            }).andExpect(status().isOk()).andExpect(jsonPath("$.rows[*].[? (@.values.flx_day =~ /^.*date:2004.*$/)]", hasSize(17568))).andExpect(jsonPath("$.rows[*]", hasSize(17568)))
//                    .andExpect(content().json(expectedJson))
                            .andReturn().getResponse().getContentAsString();
                }), dynamicTest("read SWC to csv", () -> {
                    final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/zip")
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt())
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)).
                            andExpect(request().asyncStarted()).andExpect(status().isOk()).andReturn();
                    Objects.requireNonNull(mvcResult.getRequest().getAsyncContext()).setTimeout(120000);

                    mockMvc.perform(asyncDispatch(mvcResult)).andExpect(testZip(List.of("t_flux_tours_flx.csv")));
                }))));
    }

    @Test
    @Tag("app.haute_frequence")
    @Disabled
    public void addApplicationHauteFrequence() throws Throwable {
        HauteFrequenceFixture hauteFrequenceFixture = new HauteFrequenceFixture(fixtures, mockMvc);
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "hautefrequence");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(HauteFrequenceFixture.getHauteFrequenceApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "hautefrequence", ""));
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : HauteFrequenceFixture.getHauteFrequenceReferentielFiles().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(HauteFrequenceFixture.getHauteFrequenceDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/hautefrequence").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @Tag("domain.model")
    @Disabled
    public void addDuplicatedTest() throws Throwable {
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "duplicated");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getDuplicatedApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "duplicated.yaml", "text/plain", configurationFile);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, fixtures.adminConnection.jwt(), "duplicated", ""));
        }
        String message;

        //on charge le fichier de type zone d'étude
        String typezonewithoutduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("typezonewithoutduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();


        //on recharge le fichier de type zone d'étude
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude2.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone type d'étude avec une duplication
        String typezonewithduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("typezonewithduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude_duplicate.csv", "text/plain", refStream);
            ResultActions error = mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes").file(refFile)
                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()));
            //fail();
        } catch (final ServletException e) {
            Assertions.assertInstanceOf(InvalidDatasetContentException.class, e.getCause());
            InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assertions.assertEquals(1, errors.size());
            Assertions.assertEquals(4, errors.getFirst().lineNumber());
            ValidationCheckResult validationCheckResult = errors.getFirst().validationCheckResult();
            Assertions.assertEquals(ValidationLevel.ERROR, validationCheckResult.level());
            Assertions.assertEquals("duplicatedLineInDatatype", validationCheckResult.message());
            Map<String, Object> messageParams = validationCheckResult.messageParams();
            Assertions.assertEquals("types_de_zones_etudes", messageParams.get("file"));
            Assertions.assertEquals(4, messageParams.get("lineNumber"));
            Assertions.assertArrayEquals(new Integer[]{3, 4}, ((Set) messageParams.get("otherLines")).toArray());
            Assertions.assertEquals("zone20", messageParams.get("duplicateKey"));
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();
/*
on test le dépôt d'un fichier récursif
 */


//on charge le fichier de zone d'étude
        String zonewithoutduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("zonewithoutduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();


        //on recharge le fichier de zone d'étude
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude2.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone d'étudeavec une duplication
        String zonewithduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("zonewithduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_duplicated.csv", "text/plain", refStream);
            ResultActions error = mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes").file(refFile)
                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()));
            //fail();
        } catch (final ServletException e) {
            Assertions.assertInstanceOf(InvalidDatasetContentException.class, e.getCause());
            InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assertions.assertEquals(1, errors.size());
            Assertions.assertEquals(4, errors.getFirst().lineNumber());
            ValidationCheckResult validationCheckResult = errors.getFirst().validationCheckResult();
            Assertions.assertEquals(ValidationLevel.ERROR, validationCheckResult.level());
            Assertions.assertEquals("duplicatedLineInDatatype", validationCheckResult.message());
            Map<String, Object> messageParams = validationCheckResult.messageParams();
            Assertions.assertEquals("zones_etudes", messageParams.get("file"));
            Assertions.assertEquals(4, messageParams.get("lineNumber"));
            Assertions.assertArrayEquals(new Integer[]{2, 4}, ((Set) messageParams.get("otherLines")).toArray());
            Assertions.assertEquals("site1", messageParams.get("duplicateKey"));
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();

        //on charge le fichier de zone d'étudeavec une duplication
        String zonewithmissingParent = Fixtures.getDuplicatedReferentielFiles().get("zonewithmissingparent");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithmissingParent)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_missing_parent.csv", "text/plain", refStream);
            ResultActions error = mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes").file(refFile)
                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()));
            //fail();
        } catch (final ServletException e) {
            Assertions.assertInstanceOf(InvalidDatasetContentException.class, e.getCause());
            InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assertions.assertEquals(1, errors.size());
            Assertions.assertEquals(3, errors.getFirst().lineNumber());
            ValidationCheckResult validationCheckResult = errors.getFirst().validationCheckResult();
            Assertions.assertEquals(ValidationLevel.ERROR, validationCheckResult.level());
            Assertions.assertEquals("missingParentLineInRecursiveReference", validationCheckResult.message());
            Map<String, Object> messageParams = validationCheckResult.messageParams();
            Assertions.assertEquals("zones_etudes", messageParams.get("reference"));
            Assertions.assertEquals(3L, messageParams.get("lineNumber"));
            Assertions.assertEquals("site3", messageParams.get("missingReferencesKey"));
            Assertions.assertTrue(Set.of("site3", "site1.site2", "site1", "site2").containsAll((Set) messageParams.get("knownReferences")));
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2))).andReturn().getResponse().getContentAsString();

        //on teste un dépot de fichier de données
        String dataWithoutDuplicateds = Fixtures.getDuplicatedDataFiles().get("data_without_duplicateds");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }
        //on teste le nombre de ligne
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
        }

        // on  redepose le fichier

        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            String response = mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$[0].validationCheckResult.messageParams.file", IsEqual.equalTo("dty"))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.failingRowContent", StringContains.containsString("1980-02-23")))
                    .andReturn().getResponse().getContentAsString();
        }
        // le nombre de ligne est inchangé
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
        }
        //on teste un dépot de fichier de données avec lignes dupliquées
        String dataWithDuplicateds = Fixtures.getDuplicatedDataFiles().get("data_with_duplicateds");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_with_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is4xxClientError()).andExpect(jsonPath("$[0].validationCheckResult.message", IsEqual.equalTo("duplicatedLineInDatatype"))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(5))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(6))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.Date_day.value", hasItems(1980, 2, 24, 0, 0))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.localization_zones_etudes.value.sql", IsEqual.equalTo("site1")));

        }
        // le nombre de ligne est inchangé
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
        }
    }

    @Test
    @Tag("app.olac")
    @Disabled
    public void addApplicationOLAC() throws Exception {
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "olac");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getOlaApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/olac").file(configuration)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }
        String contentAsString = mockMvc.perform(get("/api/v1/applications/olac", "ALL,ReferenceType")
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt())
                        .param("filter", "ALL")).
                andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getOlaReferentielFiles().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/olac/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getConditionPrelevementDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "condition_prelevements.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/condition_prelevements").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhysicoChimieDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "physico-chimie.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/physico-chimie").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getSondeDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "sonde_truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/sonde_truncated").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());

        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhytoAggregatedDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton_aggregated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton_aggregated").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhytoplanctonDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton__truncated").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getZooplanctonDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton__truncated").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getZooplactonBiovolumDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton_biovolumes.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton_biovolumes").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @Tag("app.foret")
    @Disabled
    public void addApplicationFORET_essai() throws Exception {
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "foret");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getForetEssaiApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "foret_essai.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/foret").file(configuration)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getForetEssaiReferentielFiles().entrySet()) {
            log.debug(e.getKey());
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        for (final Map.Entry<String, String> entry : Fixtures.getForetEssaiDataResourceName().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(entry.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/" + entry.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful());

                if ("swc_j".equals(entry.getKey())) {
                    authenticationService.setRoleAdmin();
                    String responseForBuildSynthesis = mockMvc.perform(put("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            .andExpect(jsonPath("$.SWC", hasSize(8))).andReturn().getResponse().getContentAsString();
                    String responseForGetSynthesis = mockMvc.perform(get("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                            .andExpect(jsonPath("$.SWC", hasSize(8))).andExpect(jsonPath("$.SWC[*].aggregation", containsInAnyOrder("10.0", "120.0", "160.0", "200.0", "250.0", "30.0", "55.0", "80.0"))).andReturn().getResponse().getContentAsString();
                }
            }
        }
    }

    @Test
    @Tag("app.foret")
    @Disabled
    public void addApplicationFORET() throws Exception {
        fixtures.addUserRightCreateApplication(fixtures.adminConnection.userResult().userId(), "foret");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getForetApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "foret.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/foret").file(configuration)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getForetReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/{refType}", e.getKey()).file(refFile)
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getFluxMeteoForetDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/foret/data/flux_meteo_dataResult").file(refFile)
                            .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    @Disabled
    @Tag("integration.bundle")
    public void testGetUploadBundle() throws Exception {
        URL resource = getClass().getResource(getMonsoreApplicationConfigurationWithRepositoryResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            loadApplicationMonsoere(in);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        byte[] zipResponse = getAndTestUploadBundleZip();
        extractGroovyScriptToBundleSourceFolder(zipResponse, "target/groovy");
    }

    private void extractGroovyScriptToBundleSourceFolder(byte[] zipResponse, String path) {
        Path testDir = Paths.get(path);
        InputStream is = new ByteArrayInputStream(zipResponse);
        try (ZipInputStream zi = new ZipInputStream(is)) {
            ZipEntry zipEntry;
            while ((zipEntry = zi.getNextEntry()) != null) {
                if (zipEntry.getName().equals("OpenAdomClient.groovy")) {
                    File file = testDir.resolve(zipEntry.getName()).toFile();
                    FileUtils.copyInputStreamToFile(zi, file);
                    break;
                }
            }
        } catch (IOException e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    private byte[] getAndTestUploadBundleZip() throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/upload-bundle").accept(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                //appel du sevice zip
                .andExpect(status().is2xxSuccessful()).andExpect(request().asyncStarted()).andReturn())).andExpect(result -> {
            List<ZipEntry> entries = new ArrayList<>();
            try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                ZipEntry zipEntry;
                while ((zipEntry = zi.getNextEntry()) != null) {
                    entries.add(zipEntry);
                    zi.readAllBytes();
                }
            }
            final List<String> entryNames = entries.stream().map(ZipEntry::getName).toList();
            Assertions.assertTrue(() -> entryNames.contains("OpenAdomClient.groovy"));
            Assertions.assertTrue(() -> entryNames.contains("openAdom-client-configuration.json"));
            Assertions.assertTrue(() -> entryNames.contains("LISEZ-MOI.txt"));
            Assertions.assertTrue(() -> entryNames.contains("pem/projetNK!cheminNK!dd-MM-yyyy!dd-MM-yyyy.csv"));
            Assertions.assertTrue(() -> entryNames.contains("sites/sites.csv"));
            Assertions.assertTrue(() -> entryNames.contains("projet/projet.csv"));
            Assertions.assertTrue(() -> entryNames.contains("themes/themes.csv"));
            Assertions.assertTrue(() -> entryNames.contains("unites/unites.csv"));
            Assertions.assertTrue(() -> entryNames.contains("especes/especes.csv"));
            Assertions.assertTrue(() -> entryNames.contains("variables/variables.csv"));
            Assertions.assertTrue(() -> entryNames.contains("type_de_sites/type_de_sites.csv"));
            Assertions.assertTrue(() -> entryNames.contains("type_de_fichiers/type_de_fichiers.csv"));
            Assertions.assertTrue(() -> entryNames.contains("site_theme_datatype/site_theme_datatype.csv"));
            Assertions.assertTrue(() -> entryNames.contains("valeurs_qualitatives/valeurs_qualitatives.csv"));
            Assertions.assertTrue(() -> entryNames.contains("variables_et_unites_par_types_de_donnees/variables_et_unites_par_types_de_donnees.csv"));
        }).andReturn().getResponse().getContentAsByteArray();
    }
}
