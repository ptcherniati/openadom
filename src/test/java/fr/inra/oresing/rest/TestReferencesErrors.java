package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
@Tag("domain.model")
public class TestReferencesErrors {

    public static final Map<String, String> responses = new HashMap<>();
    public static final String PASSWORD = "xxxxxxxx";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticationService authenticationService;
    private static ObjectMapper mapper = new ObjectMapper();

    private Fixtures fixtures;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @AfterAll
    static void registerErrors() throws IOException {
        String errorsAsString = new ObjectMapper().writeValueAsString(responses);
        final File errorsFile = new File("ui/cypress/fixtures/applications/errors/ref_ola_errors.json");
        final BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(errorsAsString);
        writer.close();
    }

    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(mockMvc, null, namedParameterJdbcTemplate, authenticationService);
    }

    record RecursivityTestCase(String name, String replace, String by, String expectedResponse) {
        static RecursivityTestCase of(Map.Entry<String, List<String>> entry) {
            return new RecursivityTestCase(entry.getKey(), entry.getValue().get(0), entry.getValue().get(1), entry.getValue().get(2));
        }
    }

    public static Stream<RecursivityTestCase> getRecursiviteReferentielErrorsStringReplace() {
        return Fixtures.getRecursiviteReferentielErrorsStringReplace().entrySet().stream().map(RecursivityTestCase::of);
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
                                                        final ObjectMapper mapper = new ObjectMapper();
                                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", "proprietes_taxon").file(refFile).with(csrf().asHeader()).cookie(recursivityConnection.cookie())).andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
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
                                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/condition_prelevements").file(refFile).with(csrf().asHeader()).cookie(recursivityConnection.cookie())).andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
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
                                        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc").file(refFile).with(csrf().asHeader()).cookie(repeatedColumnsConnection.cookie())).andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();

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

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey()).file(refFile).with(csrf().asHeader()).cookie(recursivityConnection.cookie())).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

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
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, recursivityConnection.cookie(), "recursivite", ""));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite").param("filter", "ALL").cookie(recursivityConnection.cookie())).andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon"))).andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_"))).andExpect(jsonPath("$.configuration.i18n.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Taxa properties"))).andReturn().getResponse().getContentAsString();

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

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey()).file(refFile).with(csrf().asHeader()).cookie(repeatedcolumnsConnection.cookie())).andDo(result -> {
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
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedcolumnsConnection.cookie(), "repeatedcolumns", ""));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/repeatedcolumns").param("filter", "ALL").cookie(repeatedcolumnsConnection.cookie())).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
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
                            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc").file(refFile).with(csrf().asHeader()).cookie(repeatedcolumnsConnection.cookie())).andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();

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

            for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedcolumnsConnection.cookie(), "repeatedcolumns", ""));
                    response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey()).file(refFile).with(csrf().asHeader()).cookie(repeatedcolumnsConnection.cookie())).andDo(result -> {
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
}