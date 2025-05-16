package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.security.JWTExtractor;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matchers;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired
    private Fixtures fixtures;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private Cookie authCookie;

    @AfterAll
    static void registerErrors() throws IOException {
        String errorsAsString = new ObjectMapper().writeValueAsString(responses);
        final File errorsFile = new File("ui/cypress/fixtures/applications/errors/ref_ola_errors.json");
        final BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(errorsAsString);
        writer.close();
    }

    @BeforeEach
    public void createUser() throws Exception {
        try {
            authUser = authenticationService.createUser(LOGIN, PASSWORD, EMAIL);
            setToActive(authUser.userId());
        } catch (AuthenticationFailure e) {
            LoginAdminResult login = authenticationService.login("poussin", "xxxxxxxx");
            authUser = CreateUserResult.of(authenticationService.getByIdOrLogin(login.id().toString()));
            setToActive(authUser.userId());
            log.info("L'utilisateur existe déjà .... login");
        }
        try {
            authenticationService.createUser("lambda", "xxxxxxxx", "lamnda@inrae.fr");
        } catch (AuthenticationFailure e) {
            log.info("L'utilisateur existe déjà .... login");
        }
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", LOGIN)
                        .param("password", PASSWORD))
                .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
        addRoleAdmin(authUser);
    }

    @Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        String sql = """
                GRANT "openAdomAdmin" TO "%1$s" WITH INHERIT TRUE
                """;

        namedParameterJdbcTemplate.update(
                String.format(sql, dbUserResult.userId().toString()),
                Map.of()
        );
    }

    @Transactional
    void setToActive(final UUID userId) {
        String sql = """
                UPDATE public.oresiuser 
                SET accountstate = 'active' 
                WHERE id = :id
                """;

        namedParameterJdbcTemplate.update(sql, Map.of("id", userId));
    }


    @Test
    void testRecursivity() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getRecursivityApplicationConfigurationResourceName());
        final Cookie recursivityCookie;
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            CreateUserResult recursivityUser = authenticationService.createUser("recursivity", PASSWORD, "recursivity@inrae.fr");
            setToActive(recursivityUser.userId());
            final UUID recursivityUserId = recursivityUser.userId();
            addUserRightCreateApplication(recursivityUserId, "recursivite");
            recursivityCookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", "recursivity")
                            .param("password", PASSWORD))
                    .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
            final String id = fixtures.getIdFromApplicationResult(
                    fixtures.loadApplication(configuration, recursivityCookie, "recursivite", "")
            );
            final String response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite")
                            .param("filter", "ALL")
                            .cookie(recursivityCookie))
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
                                .cookie(recursivityCookie))
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
                                .cookie(recursivityCookie))
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
                                .cookie(recursivityCookie))
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

    private void addUserRightCreateApplication(final UUID userId, final String pattern) throws Exception {
        mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                        .param("userIdOrLogin", userId.toString())
                        .param("applicationPattern", pattern).with(csrf().asHeader())
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.roles.user.id", IsEqual.equalTo(userId.toString())))
                .andExpect(jsonPath("$.roles.memberOf", Matchers.hasItem("applicationCreator")))
                .andExpect(jsonPath("$.authorizations", Matchers.hasItem(pattern)))
                .andExpect(jsonPath("$.id", IsEqual.equalTo(userId.toString())));
    }

    @Test
    void testRepeatedColumnsWithAllowUnexpectedColumns() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getRepeatedColumnsWithAllowUnexpectedColumnsApplicationConfigurationResourceName());
        final Cookie repeatedColumnCookie;
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "repeatedcolumns.yaml", "text/plain", in);
            CreateUserResult recursivityUser = authenticationService.createUser("repeatedcolumns", PASSWORD, "repeatedcolumns@inrae.fr");
            setToActive(recursivityUser.userId());
            final UUID recursivityUserId = recursivityUser.userId();
            addUserRightCreateApplication(recursivityUserId, "repeatedcolumns");
            repeatedColumnCookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", "repeatedcolumns")
                            .param("password", PASSWORD))
                    .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedColumnCookie, "repeatedcolumns", ""));

            final String response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/repeatedcolumns")
                            .param("filter", "ALL")
                            .cookie(repeatedColumnCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey())
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedColumnCookie))
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
                                .cookie(repeatedColumnCookie))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();

                assertEquals(e.getValue().get(2), response);
                responses.put(e.getKey(), response);
            }
        }
    }

    @Test
    void testRepeatedColumns() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getRepeatedColumnsApplicationConfigurationResourceName());
        final Cookie repeatedColumnsCookie;
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "repeatedcolumns.yaml", "text/plain", in);
            CreateUserResult recursivityUser = authenticationService.createUser("repeatedcolumns", PASSWORD, "repeatedcolumns@inrae.fr");
            setToActive(recursivityUser.userId());
            final UUID recursivityUserId = recursivityUser.userId();
            addUserRightCreateApplication(recursivityUserId, "repeatedcolumns");
            repeatedColumnsCookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", "repeatedcolumns")
                            .param("password", PASSWORD))
                    .andReturn().getResponse().getCookie(JWTExtractor.JWT_COOKIE_NAME);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, repeatedColumnsCookie, "repeatedcolumns", ""));
            final String response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/repeatedcolumns")
                            .param("filter", "ALL")
                            .cookie(repeatedColumnsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        String response;
        for (final Map.Entry<String, String> e : Fixtures.getRepeatedColumnsReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/{refType}", e.getKey())
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedColumnsCookie))
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
        for (final Map.Entry<String, List<String>> e : Fixtures.getRepeatedColumnsDataErrorsStringReplace().entrySet()) {
            final String textCsvModify = monRepositoryCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (final InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "SWC_truncated.csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/repeatedcolumns/data/swc")
                                .file(refFile).with(csrf().asHeader())
                                .cookie(repeatedColumnsCookie))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();

                assertEquals(e.getValue().get(2), response);
                responses.put(e.getKey(), response);
            }
        }
    }


}