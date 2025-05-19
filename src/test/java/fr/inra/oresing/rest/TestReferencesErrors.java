package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public static final String LOGIN = "poussinreferenceserrors";
    public static final String PASSWORD = "xxxxxxxx";
    public static final String EMAIL = "poussinreferenceserrors@inrae.fr";
    private static CreateUserResult authUser = null;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JsonRowMapper jsonRowMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthenticationService authenticationService;

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
        fixtures = new Fixtures(
                mockMvc,
                null,
                namedParameterJdbcTemplate,
                authenticationService
        );
    }


    @Test
    void testRecursivity() throws Exception {
        Fixtures.UserConnection recursivityConnection;
        final URL resource = getClass().getResource(Fixtures.getRecursivityApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            Fixtures.CreateUser recursivity = new Fixtures.CreateUser("recursivity", PASSWORD, "recursivity@inrae.fr");
            recursivityConnection = fixtures.createUserForUserDefinition(recursivity, true, false);
            fixtures.addUserRightCreateApplication(recursivityConnection.userResult().userId(), "recursivite");
            final String id = fixtures.getIdFromApplicationResult(
                    fixtures.loadApplication(configuration, recursivityConnection.cookie(), "recursivite", "")
            );
            final String response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite")
                            .param("filter", "ALL")
                            .cookie(recursivityConnection.cookie()))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon")))
                    .andExpect(jsonPath("$.configuration.dataDescription.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_")))
                    .andExpect(jsonPath("$.configuration.i18n.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Taxa properties")))
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        // Ajout de referentiel
        final String proprietes_taxon_path = Fixtures.getRecursiviteReferentielOrderFiles().get("proprietes_taxon");
        final StringBuilder textBuilder = new StringBuilder();
        try (final InputStream refStream = getClass().getResourceAsStream(proprietes_taxon_path)) {
            assert refStream != null;
            try (final Reader reader = new BufferedReader(new InputStreamReader
                    (refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuilder.append((char) c);
                }
            }
        }
        final String monCSV = textBuilder.toString();

        for (final Map.Entry<String, List<String>> e : Fixtures.getRecursiviteReferentielErrorsStringReplace().entrySet()) {
            final String textCsvModify = monCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getKey() + ".csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", "proprietes_taxon")
                                .file(refFile).with(csrf().asHeader())
                                .cookie(recursivityConnection.cookie()))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();
                JSONAssert.assertEquals(e.getValue().get(2), response, JSONCompareMode.NON_EXTENSIBLE);
                responses.put(e.getKey(), response);
            }
        }
        //System.out.println(responses);
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                .file(refFile).with(csrf().asHeader())
                                .cookie(recursivityConnection.cookie()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        // ajout de data
        // test repository
        final String site = "leman";
        final URL resources = getClass().getResource(Fixtures.getConditionsPrelevementRepositoryResourceName(site));
        final StringBuilder textBuild = new StringBuilder();
        try (final InputStream refStream = Objects.requireNonNull(resources).openStream()) {
            try (final Reader reader = new BufferedReader(new InputStreamReader
                    (refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuild.append((char) c);
                }
            }
        }
        final String monRepositoryCSV = textBuild.toString();
        for (final Map.Entry<String, List<String>> e : Fixtures.getRecursiviteDataErrorsStringReplace().entrySet()) {
            final String textCsvModify = monRepositoryCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "suivi_des_lacs_leman_conditions_prelevements_01-01-2020_31-12-2020.csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/data/condition_prelevements")
                                .file(refFile).with(csrf().asHeader())
                                .cookie(recursivityConnection.cookie()))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();
                final Matcher m = Pattern.compile("(.*)\"referenceValues\":(\\{(.*?)\\})(.*)").matcher(response);
                responses.put(e.getKey(), response);
                if (m.matches()) {
                    response = String.format("%s[%s]%s",
                            m.group(1),
                            Arrays.stream(m.group(3).split(","))
                                    .map(s -> s.split(":")[0])
                                    .sorted()
                                    .collect(Collectors.joining(",")),
                            m.group(4));
                }
                JSONAssert.assertEquals(e.getValue().get(2), response, JSONCompareMode.NON_EXTENSIBLE);
            }
        }
        //System.out.println(responses);
    }

    @Test
    void testRepeatedColumnsWithAllowUnexpectedColumns() throws Exception {
        Fixtures.CreateUser repeatedcolumns = new Fixtures.CreateUser("repeatedcolumns", PASSWORD, "repeatedcolumns@inrae.fr");
        Fixtures.UserConnection repeatedcolumnsConnection = fixtures.createUserForUserDefinition(repeatedcolumns, true, false);
        final URL resource = getClass().getResource(Fixtures.getRepeatedColumnsWithAllowUnexpectedColumnsApplicationConfigurationResourceName());
        String response;
        fixtures.addUserRightCreateApplication(repeatedcolumnsConnection.userResult().userId(), "repeatedcolumns");
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "repeatedcolumns.yaml", "text/plain", in);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedcolumnsConnection.cookie(), "repeatedcolumns", ""));

            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/repeatedcolumns")
                            .param("filter", "ALL")
                            .cookie(repeatedcolumnsConnection.cookie()))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey())
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedcolumnsConnection.cookie()))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() > 300) {
                                log.error(e.getKey());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        // test repository
        final URL resources = getClass().getResource(Fixtures.getSWCRepositoryResourceName());
        final StringBuilder textBuild = new StringBuilder();
        try (final InputStream refStream = Objects.requireNonNull(resources).openStream()) {
            try (final Reader reader = new BufferedReader(new InputStreamReader
                    (refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuild.append((char) c);
                }
            }
        }
        final String monRepositoryCSV = textBuild.toString();
        for (final Map.Entry<String, List<String>> e : Fixtures.getRepeatedColumnsgWithAllowUnexpectedColumnsDataErrorsStringReplace().entrySet()) {
            final String textCsvModify = monRepositoryCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "SWC_truncated.csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc")
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedcolumnsConnection.cookie()))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();

                assertEquals(e.getValue().get(2), response);
                responses.put(e.getKey(), response);
            }
        }
    }

    @Test
    void testRepeatedColumns() throws Exception {
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
                    response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey())
                                    .file(refFile).with(csrf().asHeader())
                                    .cookie(repeatedcolumnsConnection.cookie()))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() > 300) {
                                    log.error(e.getKey());
                                }
                            })
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                            .andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        // test repository
        final URL resources = getClass().getResource(Fixtures.getSWCRepositoryResourceName());
        final StringBuilder textBuild = new StringBuilder();
        try (final InputStream refStream = Objects.requireNonNull(resources).openStream()) {
            try (final Reader reader = new BufferedReader(new InputStreamReader
                    (refStream, StandardCharsets.UTF_8))) {
                int c;
                while ((c = reader.read()) != -1) {
                    textBuild.append((char) c);
                }
            }
        }
        final String monRepositoryCSV = textBuild.toString();
        for (final Map.Entry<String, List<String>> e : Fixtures.getRepeatedColumnsDataErrorsStringReplace().entrySet()) {
            final String textCsvModify = monRepositoryCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "SWC_truncated.csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc")
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedcolumnsConnection.cookie()))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();

                assertEquals(e.getValue().get(2), response);
                responses.put(e.getKey(), response);
            }
        }
    }
}