package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdministratorForSystemException;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.services.RelationalService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("integration.rest")
@Slf4j
public class OreSiResourcesTest {

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
    @Autowired
    RelationalService relationalService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JsonRowMapper jsonRowMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private Fixtures fixtures;
    private Cookie authCookie;
    private Cookie lambdaCookie;
    private UUID authUserId;
    private CreateUserResult lambdaUser;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private UserRepository userRepository;

    public static void registerFile(final String filePath, final String jsonContent) throws IOException {
        final File errorsFile = new File(filePath);
        log.debug(errorsFile.getAbsolutePath());
        final BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(jsonContent);
        writer.close();
    }

    private static InputStream changeToV2(final InputStream inputStream) throws IOException {
        assert inputStream != null;
        final String yaml = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        final String yamlVersion2 = yaml.replace("OA_version: 3.0.1", "OA_version: 3.0.2");
        return new ByteArrayInputStream(yamlVersion2.getBytes(StandardCharsets.UTF_8));
    }

    private static ResultMatcher testZip(final List<String> expectedEntries) {
        return result -> {
            final List<ZipEntry> entries = new ArrayList<>();
            final byte[] contentAsByteArray = result.getResponse().getContentAsByteArray();
            Files.write(Path.of("/tmp/data.zip"), contentAsByteArray);
            final List<String> findedEntries = new LinkedList<>();
            try (final ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(contentAsByteArray))) {
                ZipEntry entry = zi.getNextEntry();
                while (entry != null) {
                    Assertions.assertNotNull(entry, "l'entrée est nulle ");
                    findedEntries.add(entry.getName());
                    entry = zi.getNextEntry();
                }
                expectedEntries
                        .forEach(e ->
                                Assertions.assertTrue(() -> findedEntries.contains(e), String.format("Le zip doit contenir %s", e)));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    @Tag("core.basic")
    public void testDatabaseUser() throws SQLException {
        String currentUser = getCurrentDatabaseUser();
        Assertions.assertEquals("openAdomTechUser", currentUser, "Le test devrait être exécuté en tant qu'openadomTechUser");
    }

    private String getCurrentDatabaseUser() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT CURRENT_USER")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        throw new SQLException("Impossible d'obtenir l'utilisateur actuel de la base de données");
    }

    @Test
    @Tag("SWAGGER_BUILD")
    @Tag("integration.rest")
    public void services_model() throws Exception {
        final String services_model = mockMvc.perform(get("/api-docs")
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                )
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
        registerFile("documentations/services_model.json", services_model);

    }

    @BeforeEach
    public void createUser() throws Exception {
        try {
            final OreSiUser user = authenticationService.getByIdOrLogin("lambda");
            lambdaUser = Optional.ofNullable(user)
                    .map(CreateUserResult::of)
                    .orElseThrow();
        } catch (final Exception e) {
            lambdaUser = createUserIfNotExists("lambda", "xxxxxxxx", "lambda@inrae.fr");

        }
        setToActive(lambdaUser.userId());
        lambdaCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "lambda")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        CreateUserResult authUser;
        try {
            final OreSiUser user = authenticationService.getByIdOrLogin("poussin");
            authUser = CreateUserResult.of(user);
        } catch (final Exception e) {
            authUser = createUserIfNotExists("poussin", "xxxxxxxx", "poussin@inrae.fr");
        }
        authUserId = authUser.userId();
        setToActive(authUserId);
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        addRoleAdmin(authUser);
    }

    @Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        namedParameterJdbcTemplate.update("grant \"openAdomAdmin\" to \"" + dbUserResult.userId().toString() + "\" WITH INHERIT TRUE;", Map.of());
    }

    @Transactional
    void setToActive(final UUID userId) {
        namedParameterJdbcTemplate.update(
                """
                        UPDATE public.oresiuser SET accountstate = 'active' WHERE id = :id
                        """, Map.of("id", userId));
    }


    @Test
    @Tag("OTHERS_TEST")
    @Tag("app.monsoere")
    @Tag("MONSOERE")
    public void addApplicationMonsore() throws Exception {
        final String appId;

        CreateUserResult monsoereUser = createUserIfNotExists("monsoresimple", "xxxxxxxx", "monsoresimple@inrae.fr");
        setToActive(monsoereUser.userId());
        final UUID monsoreUserId = monsoereUser.userId();
        OreSiUser publicUser = userRepository.findByLogin("_public_").orElse(null);
        assert publicUser != null;
        UUID publicUserId = publicUser.getId();
        Assertions.assertEquals(UUID.fromString("9032ffe5-bfc1-453d-814e-287cd678484a"), publicUserId);
        final Cookie monsoreCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "monsoresimple")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult withRightsUserResult = createUserIfNotExists("withrigths", "xxxxxxxx", "withrigths@inrae.fr");
        final String withRigthsUserId = withRightsUserResult.userId().toString();
        setToActive(withRightsUserResult.userId());
        final Cookie withRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        URL resource = getClass().getResource(Fixtures.getMonsoreApplicationConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsoresimple.yaml", "text/plain", in);

            // on n'a pas le droit de creer de nouvelle application
            NotOpenAdomAdministratorForSystemException resolvedException =
                    (NotOpenAdomAdministratorForSystemException) fixtures.loadApplicationWithError(
                            configuration,
                            monsoreCookie,
                            "monsoresimple"
                    );
            addUserRightCreateApplication(monsoreUserId, "monsoresimple");
            assert resolvedException != null;

            final MvcResult resultApplication = fixtures.loadApplication(configuration, monsoreCookie, "monsoresimple", "");
            appId = fixtures.getIdFromApplicationResult(resultApplication);
        } catch (final Throwable e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

        String response = mockMvc.perform(get("/api/v1/applications/{appId}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("filter", "ALL")
                        .cookie(monsoreCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // id
                .andExpect(jsonPath("$.id", Is.is(appId)))
                .andReturn().getResponse().getContentAsString();

        final ApplicationResult applicationResult = (ApplicationResult) jsonRowMapper.readValue(response, ApplicationResult.class);

        Assertions.assertEquals("Fichier de test de l'application brokenADOM version initiale", applicationResult.comment());
        Assertions.assertEquals("monsoresimple", applicationResult.name());
        Assertions.assertEquals(new TreeSet<>(Set.of("themes", "especes", "site_theme_datatype", "variables", "type_de_sites", "unites", "projet", "valeurs_qualitatives", "type_de_fichiers", "variables_et_unites_par_types_de_donnees")), new TreeSet<>(applicationResult.references().keySet()));
        Assertions.assertEquals(Set.of("pem"), ((LinkedHashMap) applicationResult.dataTypes()).keySet());

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getMonsoreReferentielEspecestoTrimFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);


                response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(monsoreCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        //test de la reference especetoTrim
        final String getEspecesResponse = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/especes/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(monsoreCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rows[*][?(@.hierarchicalKey=='especeKlpf')].values.esp_definition_fr", IsNull.notNullValue()))
                //la colonne "esp_nom" et la colonne "esp_definition_fr"  ont bien été lues sans espaces
                .andExpect(jsonPath("$.rows[*][?(@.hierarchicalKey=='especesKlpf')].values.esp_definition_fr", hasItem("LPF")))
                //les valeurs de la colonne "esp_definition_fr"  ont bien été lues sans espaces
                .andReturn().getResponse().getContentAsString();

        // Ajout de referentiel
        {
            for (final Map.Entry<String, String> e : Fixtures.getMonsoreReferentielFiles().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                    response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", e.getKey())
                                    .file(refFile)
                                    .cookie(monsoreCookie))
                            .andDo(result -> {
                                final int status = result.getResponse().getStatus();
                                if (status > 300) {
                                    System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                }
                            })
                            .andDo(result -> {
                                final int status = result.getResponse().getStatus();
                                if (status > 300) {
                                    System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                }
                            })
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                            .andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
            mockMvc.perform(get("/api/v1/applications/{appId}", appId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .param("filter", "ALL")
                            .cookie(monsoreCookie))
                    .andExpect(status().isOk());

            final String getReferencesResponse = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/sites/json")
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(monsoreCookie))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    //.andExpect(jsonPath("$.totalRows", is(9)))
                    .andExpect(jsonPath("$.rows", hasSize(9)))
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform(
                            asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsoresimple/data/sites/csv")
                                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                            .cookie(monsoreCookie))
                                    .andExpect(status().isOk())
                                    .andExpect(request().asyncStarted())
                                    .andReturn()
                            )
                    )
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                    .andExpect(result -> {
                        final List<String> expected = """
                                "tze_type_nom";"zet_chemin_parent";"definition";"Site name";"zet_nom_key"
                                "Watershed";"";"Watershed Nivelle";"Nivelle";"nivelle"
                                "Watershed";"";"Oir catchment";"Oir";"oir"
                                "Watershed";"";"Watershed Scarff";"Scarff";"scarff"
                                "Platform";"- Nivelle";"";"P1";"p1"
                                "Platform";"- Oir";"";"P1";"p1"
                                "Platform";"oir - P1";"";"A";"a"
                                "Platform";"oir - P1";"";"B";"b"
                                "Platform";"- Oir";"";"P2";"p2"
                                "Platform";"- Scarff";"";"P1";"p1\""""
                                .lines().collect(Collectors.toCollection(LinkedList::new));
                        final List<String> actual = new String(result.getResponse().getContentAsByteArray())
                                .lines().collect(Collectors.toCollection(LinkedList::new));
                        Assertions.assertEquals(expected, actual, "Bad site.csv ");
                    });
        }
        resource = getClass().getResource(Fixtures.getPemDataResourceName());

        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", "pem")
                            .file(refFile)
                            .cookie(monsoreCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }

        // ajout de data
        resource = getClass().getResource(Fixtures.getPemDataResourceName());
        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            // sans droit on ne peut pas
            mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'monsoresimple'"))
                    .andReturn().getResponse().getContentAsString();
            //ajout de droits withRignesthsUserId
            if (true) {// TODO remove
                return;
            }

            String jsonRightsForMonsoere = getJsonRightsforRestrictions(withRigthsUserId,
                    List.of(OperationType.publication.name()),
                    "pem",
                    "type_de_sitesKplateforme.sitesKoir.sitesKoir__p1",
                    "1984,1,1",
                    "1984,1,5",
                    monsoreCookie);
            String jsonRightsForMonsoereId = JsonPath.parse(jsonRightsForMonsoere).read("$.authorizationId");
            //avec les droits on peut publier
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(monsoreCookie/*withRigthsCookie*/))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(result.getResolvedException().getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));

            //sans droit on ne peut pas
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();

            //ajout de droits public
            String publicRightsId = JsonPath.parse(setJsonRightsForMonsoere(monsoreCookie, "9032ffe5-bfc1-453d-814e-287cd678484a", OperationType.publication.name(), "pem")).read("$.authorizationId");


            //avec les droits public on peut publier même sans droit
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            // on supprime les droits public
            mockMvc.perform(delete("/api/v1/applications/monsoresimple/authorization/" + publicRightsId)
                            .cookie(monsoreCookie))
                    .andExpect(status().is2xxSuccessful());

            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }
        // ajout de data avec trim à faire
        resource = getClass().getResource(Fixtures.getPemDataToTrimResourceName());
        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            final String actualJson = getPemData(monsoreCookie);
            log.debug(StringUtils.abbreviate(actualJson, 50));
        }
        final String contentAsString = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                        .cookie(monsoreCookie))
                .andExpect(jsonPath("$.rows[*].values" +
                                    "[?(@.projet.value=='projet_atlantique' )]" +
                                    "[?(@.date.value=='date:1984-01-01T00:00:00:dd/MM/yyyy' )]" +
                                    "[?(@.site.chemin=='plateforme.nivelle.nivelle__p1' )]" +
                                    "[?(@.espece.value=='lpf' )]" +
                                    "[?(@['Couleur des individus'].value=='couleur_des_individus__bleu' )]" +
                                    "['Nombre d\\'individus'].value",
                        hasItems(54)))
                .andReturn().getResponse().getContentAsString();

        net.minidev.json.JSONArray rowIds = JsonPath.parse(contentAsString)
                .read("$.rows[0,1].rowId");
        final List<String> filterByRowId = new LinkedList<>();
        final int len = rowIds.size();
        for (Object rowId : rowIds) {
            filterByRowId.add(rowId.toString());
        }

        final String query = String.format("""
                        {
                          "rowIds": [%s]
                        }""",
                filterByRowId.stream()
                        .map(s -> String.format("""
                                "%s"
                                """, s))
                        .collect(Collectors.joining(",")));
        mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                        .param("downloadDatasetQuery", query)
                        .cookie(monsoreCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows.length()", equalTo(2)))
                .andExpect(jsonPath("$.rows[*].rowId", hasItems(filterByRowId.get(0), filterByRowId.get(1))))
                .andReturn().getResponse().getContentAsString();

        //récupération du data

        try (final InputStream pem = getClass().getResourceAsStream(Fixtures.getPemDataResourceName())) {
            final String data = IOUtils.toString(Objects.requireNonNull(pem), StandardCharsets.UTF_8);
            final String wrongData = data.replace("plateforme", "entete_inconnu");
            final byte[] bytes = wrongData.getBytes(StandardCharsets.UTF_8);
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", bytes);
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(monsoreCookie))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        } catch (final IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }

        // list des types de data
        response = mockMvc.perform(get("/api/v1/applications/monsoresimple/data")
                        .cookie(monsoreCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        log.debug(StringUtils.abbreviate(response, 50));

        {
            final String expectedJson = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsoresimple/compare/export.json")), StandardCharsets.UTF_8);
            final JSONArray jsonArray = new JSONArray(expectedJson);
            final List<String> list = new ArrayList<>();

            final String actualJson = getPemData(monsoreCookie);
            log.debug(StringUtils.abbreviate(actualJson, 50));
        }
        String filter = """
                {
                  "componentFilters": [
                    {
                      "componentKey": {
                        "variable": "date",
                        "component": "value"
                      },
                      "intervalValues": {
                        "from": "01/01/1984",
                        "to": "02/01/1984"
                      }
                    },
                    {
                      "componentKey": {
                        "variable": "Nombre d'individus",
                        "component": "value"
                      },
                      "intervalValues": {
                        "from": 20,
                        "to": 29
                      }
                    },
                    {
                      "componentKey": {
                        "variable": "Couleur des individus",
                        "component": "value"
                      },
                      "filter": "couleur_des_individus__vert"
                    }
                  ],
                  "componentOrderBy": [
                    {
                      "componentKey": {
                        "variable": "site",
                        "component": "plateforme"
                      },
                      "order": "ASC",
                      "type": null,
                      "format": null
                    }
                  ]
                }""";
        {
            final String actualJson = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                            .cookie(monsoreCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows", hasSize(272)))
                    .andExpect(jsonPath("$.rows[*].values.date[?(@.value == 'date:1984-01-01T00:00:00:dd/MM/yyyy')]", hasSize(56)))
                    .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'][?(@.value ==25)]", hasSize(32)))
                    .andExpect(jsonPath("$.rows[*].values['Couleur des individus'][?(@.value =='couleur_des_individus__vert')]", hasSize(96)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualJson, 50));

        }

        // restitution de data csv
        {
            final String expectedCsv = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsoresimple/compare/export.csv")), StandardCharsets.UTF_8);
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/zip")
                                    .cookie(monsoreCookie)
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                            .andExpect(status().isOk())
                            .andExpect(request().asyncStarted())
                            .andReturn()))
                    .andExpect(testZip(List.of("pem.csv",
                            "reference/especes.csv",
                            "reference/type_de_sites.csv",
                            "reference/unites.csv",
                            "reference/projet.csv",
                            "reference/valeurs_qualitatives.csv",
                            "reference/sites.csv")
                    ));
        }

        try (final InputStream in = getClass().getResourceAsStream(Fixtures.getPemDataResourceName())) {
            final String csv = IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
            final String invalidCsv = csv
                    .replace("projet_manche", "projet_manch")
                    .replace("projet_atlantique", "projet_atlantiqu");
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", invalidCsv.getBytes(StandardCharsets.UTF_8));
            response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem")
                            .file(refFile)
                            .cookie(monsoreCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
            Assertions.assertTrue(response.contains("projet_manch"));
            Assertions.assertTrue(response.contains("projet_atlantiqu"));
            final String finalResponse = response;
            Assertions.assertTrue(() -> finalResponse.contains("41"), "Il faut mentionner les lignes en erreur");
            Assertions.assertTrue(() -> finalResponse.contains("42"), "Il faut mentionner les lignes en erreur");
            Assertions.assertTrue(() -> finalResponse.contains("43"), "Il faut mentionner les lignes en erreur");
            Assertions.assertTrue(() -> finalResponse.contains("10"), "Il faut mentionner les lignes en erreur");
            Assertions.assertFalse(() -> finalResponse.contains("141"), "L'erreur doit être tronquée");
            Assertions.assertFalse(() -> finalResponse.contains("142"), "L'erreur doit être tronquée");
            Assertions.assertFalse(() -> finalResponse.contains("143"), "L'erreur doit être tronquée");
        }
        String getMonsoere = mockMvc.perform(get("/api/v1/applications/monsoresimple")
                        .cookie(authCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        String getSites = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/sites")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String getTypeSites = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/type_de_sites")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String getProjet = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/projet")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String getPem = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/api/v1/applications/monsoresimple/data/pem")
                        .param("downloadDatasetQuery", filter)
                        .cookie(monsoreCookie))
                .andExpect(status().is2xxSuccessful())
                .andDo(result -> {
                    String[] uuids = result.getResponse().getContentAsString().split(",");
                    final int expectedUUIDs = 24;
                    Assertions.assertEquals(expectedUUIDs, uuids.length, String.format("On attend %d lignes; la requête en renvoie %d", expectedUUIDs, uuids.length));

                });
        // fix issue #165
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
        /*filter = filterPattern.formatted("\"15\"", null);
        mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                            .cookie(monsoreCookie)
                            .param("downloadDatasetQuery", filter)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows", Matchers.not(Matchers.empty())));*/

        filter = filterPattern.formatted(null, "{\"from\":\"15\",\"to\":\"15\"}");
        mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                        .cookie(monsoreCookie)
                        .param("downloadDatasetQuery", filter)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", not(empty())));

        String getAuthorizations = mockMvc.perform(get("/api/v1/applications/monsoresimple/authorization")
                        .cookie(withRigthsCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String getGrantable = mockMvc.perform(get("/api/v1/applications/monsoresimple/grantable")
                        .cookie(withRigthsCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        registerFile("ui/cypress/fixtures/applications/ore/monsore/monsoere.json", getMonsoere);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/pem.json", getPem);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/reference/type_de_sites.json", getTypeSites);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/reference/sites.json", getSites);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/reference/projet.json", getProjet);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/authorisation/grantable.json", getGrantable);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/authorisation/authorizations.json", getAuthorizations);

    }

    private CreateUserResult createUserIfNotExists(String login, String password, String mail) throws Exception {
        if (mockMvc.perform(post("/api/v1/login")
                        .param("login", login)
                        .param("password", password))
                    .andReturn()
                    .getResponse().getStatus() > 300) {
            return authenticationService.createUser(login, password, mail);
        } else {
            OreSiUser userByLogin = userRepository.findByLogin(login).orElse(null);
            return CreateUserResult.of(Objects.requireNonNull(userByLogin));
        }

    }

    private String getPemData(final Cookie monsoreCookie) throws Exception {
        return mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables").isArray())
                .andExpect(jsonPath("$.variables", hasSize(6)))
                .andExpect(jsonPath("$.variables").value(Stream.of("date", "projet", "site", "Couleur des individus", "Nombre d'individus", "espece").collect(Collectors.toList())))
                .andExpect(jsonPath("$.checkedFormatComponents.DateType", IsNull.notNullValue()))
                .andExpect(jsonPath("$.checkedFormatComponents.ReferenceType", IsNull.notNullValue()))
                .andExpect(jsonPath("$.checkedFormatComponents.IntegerType", IsNull.notNullValue()))
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows", hasSize(272)))
                //.andExpect(jsonPath("$.rows.value").value(list))
                .andExpect(jsonPath("$.rows[*].values.date.value", hasSize(272)))
                .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'].unit", hasSize(272)))
                .andExpect(jsonPath("$.rows[*].values['Couleur des individus'].unit", hasSize(272)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Tag("OTHERS_TEST")
    @Tag("integration.rest")
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
            addUserRightCreateApplication(authUserId, "multiplicity");

            final String id = fixtures.getIdFromApplicationResult(
                    fixtures.loadApplication(configuration, authCookie, "multiplicity", "")
            );

            mockMvc.perform(get("/api/v1/applications/multiplicity", "ALL,ReferenceType")
                            .cookie(authCookie)
                            .param("filter", "ALL"))
                    .andExpect(status().is2xxSuccessful());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getMultiplicityReferencesFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                final String response = mockMvc.perform(multipart("/api/v1/applications/multiplicity/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        mockMvc.perform(get("/api/v1/applications/multiplicity/data/reference1/json")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[0].values.projets", hasItems(4, 5, 9)))
                .andExpect(jsonPath("$.rows[0].values.names", hasItems("toto1.1", "toto1.2", "toto1.3")))
                .andExpect(jsonPath("$.rows[*].values[?(@.names==['toto1.1','toto1.2','toto1.3'])]", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].values.durations", hasItems(-4.5, 5.6, 3.2)))
                .andExpect(jsonPath("$.rows[0].values.dates", hasItems("date:2014-01-20T00:00:00:dd/MM/yyyy", "date:2014-06-23T00:00:00:dd/MM/yyyy")));
        mockMvc.perform(get("/api/v1/applications/multiplicity/data/reference2/json")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values.reference1[*]", hasItems("toto__toto1", "toto__toto2", "tutu__tutu1", "tutu__tutu2")));
        try (final InputStream refStream = getClass().getResourceAsStream(Fixtures.getMultiplicityManyData())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "bugs.csv", "text/plain", refStream);

            mockMvc.perform(get("/api/v1/applications/multiplicity/data/bugs/json")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$.referenceTypeForReferencingColumns.reference", is("reference1"))) // TODO
                    //.andExpect(jsonPath("$.referenceTypeForReferencingColumns.references", is("reference1"))) // TODO
                    .andExpect(jsonPath("$.rows[0].values.dates", hasItems("date:2002-01-23T00:00:00:dd/MM/yyyy", "date:2002-01-24T00:00:00:dd/MM/yyyy")))
                    .andExpect(jsonPath("$.rows[0].values.projets", hasItems(1, 2)))
                    .andExpect(jsonPath("$.rows[0].values.fichiers", hasItems("file1", "file2")))
                    .andExpect(jsonPath("$.rows[0].values.durations", hasItems(3.2, 5.4)))
                    .andExpect(jsonPath("$.rows[0].values.references", hasItems("toto__toto1", "tutu__tutu1")))
                    .andReturn().getResponse().getContentAsString();
        }
        relationalService.createViews("multiplicity", ViewStrategy.VIEW);
    }

    @Test
    @Tag("core.config")
    @Disabled
    public void addApplicationWithComputedComponentsWithReferences() throws Exception {
        final URL resource = getClass().getResource(Fixtures.getApplicationWithComputedComponentsWithReferences());

        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "minautor");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "minautor", ""));

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getApplicationWithComputedComponentsWithReferencesReferences().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/minautor/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        // Ajout de data
        for (final Map.Entry<String, String> e : Fixtures.getApplicationWithComputedComponentsWithReferencesData().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/minautor/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        mockMvc.perform(get("/api/v1/applications/minautor/data/dataset/json")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values.informations.site", hasSize(7)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.site", hasSize(7)))
                .andExpect(jsonPath("$.rows[*].values.informations.parcelle", hasSize(7)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.parcelle", hasSize(7)))
                .andExpect(jsonPath("$.rows[*].values.informations.bloc", hasSize(7)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.bloc", hasSize(7)));
    }

    private String loadApplicationMonsoere(InputStream in) throws Throwable {
        final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
        //définition de l'application
        addUserRightCreateApplication(authUserId, "monsore");
        final MvcResult resultForValidateMonsore = fixtures.validateApplication(configuration, authCookie);
        List<ReactiveTypeResult> results = Fixtures.getResults(resultForValidateMonsore);
        Assertions.assertTrue(results.stream().noneMatch(obj -> false));
        final String responseForTestingmonsoere = resultForValidateMonsore.getResponse().getContentAsString();
        final MvcResult resultForCreateMonsore = fixtures.loadApplication(configuration, authCookie, "monsore", "");
        registerFile("ui/cypress/fixtures/applications/ore/monsore/validateMonsore.txt", responseForTestingmonsoere);

        return resultForCreateMonsore.getResponse().getContentAsString();
    }

    @Test
    @Tag("OTHERS_TEST")
    @Tag("app.monsoere")
    @Tag("MONSOERE")
    public void addApplicationMonsoreWithRepository() throws Exception {
        URL resource = getClass().getResource(Fixtures.getMonsoreApplicationConfigurationWithRepositoryResourceName());
        final String oirFilesUUID;
        try (final InputStream in = Objects.requireNonNull(resource).openStream();
             final InputStream inV2 = changeToV2(Objects.requireNonNull(resource).openStream())) {
            final String responseForCreatemonsoere = loadApplicationMonsoere(in);

            final MockMultipartFile configurationV2 = new MockMultipartFile("file", "monsore.yaml", "text/plain", inV2);
            final MvcResult resultForChangeMonsore = fixtures.changeConfiguration(configurationV2, authCookie, "monsore", "monsorev2");
            final String responseForChangemonsoere = resultForChangeMonsore.getResponse().getContentAsString();
            final String id = fixtures.getIdFromApplicationResult(resultForChangeMonsore);

            registerFile("ui/cypress/fixtures/applications/ore/monsore/createMonsore.txt", responseForCreatemonsoere);
            registerFile("ui/cypress/fixtures/applications/ore/monsore/changeMonsore.txt", responseForChangemonsoere);
            Assertions.assertEquals(1, Arrays.stream(getApplicationsFlux(authCookie, "ALL"))
                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> JsonPath.parse(s).read("$.result.application.data", List.class).contains("sites"))
                    .filter(s -> !JsonPath.parse(s).read("$.result.application.data", List.class).contains("type de fichiers"))
                    .count());
            mockMvc.perform(get("/api/v1/applications/monsore")
                            .cookie(authCookie)
                            .param("filter", "ALL"))
                    .andExpect(status().is2xxSuccessful())
                    ///vérification de la sauvegarde des tags
                    .andExpect(jsonPath("$.data.type_de_sites.tags[*].tagName", contains("context")))
                    .andExpect(jsonPath("$.data.sites.tags[*].tagName", contains("context")))
                    .andExpect(jsonPath("$.data.projet.tags[*].tagName", hasItems("context", "data", "test")))
                    .andExpect(jsonPath("$.data.site_theme_datatype.tags[*].tagName", contains("context")))
                    .andExpect(jsonPath("$.data.especes.tags[*].tagName", contains("data")))
                    .andExpect(jsonPath("$.data.especes.componentDescriptions.esp_nom.tags[*].tagName", contains("test")))
                    .andExpect(jsonPath("$.data.type_de_fichiers.tags[*].tagDefinition", contains("HIDDEN_TAG")))
                    .andExpect(jsonPath("$.data.variables.tags[*].tagName", contains("data")))
                    .andExpect(jsonPath("$.data.unites.tags[*].tagName", contains("data")))
                    .andExpect(jsonPath("$.data.valeurs_qualitatives.tags[*].tagName", contains("data")))
                    .andExpect(jsonPath("$.data.variables_et_unites_par_types_de_donnees.tags[*].tagName", contains("data")))
                    .andExpect(jsonPath("$.internationalization.tags.context.fr", Is.is("Contexte")))
                    .andExpect(jsonPath("$.rightsRequest.description.formFields.endDate", not(empty())))
                    .andExpect(jsonPath("$.configuration.rightsRequest.formFields.organization", not(empty())))
                    .andExpect(jsonPath("$.data.pem.tags[*].tagName", hasItem("data")))
                    .andExpect(jsonPath("$.data.pem.componentDescriptions.projet.tags[*].tagName", hasItem("test")))
                    .andExpect(jsonPath("$.data.pem.componentDescriptions.projet.tags[*].tagOrder", hasItem(2)))
                    .andExpect(jsonPath("$.data.pem.componentDescriptions.espece.tags[*].tagDefinition", hasItem("NO_TAG")));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        final CreateUserResult withRightsUserResult = createUserIfNotExists("withrigths", "xxxxxxxx", "withrigths@inrae.fr");
        setToActive(withRightsUserResult.userId());
        final String withRigthsUserId = withRightsUserResult.userId().toString();
        final Cookie withRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        String typeDeSites = Fixtures.getMonsoreReferentielFiles().get("type_de_sites");

        String sites = Fixtures.getMonsoreReferentielFiles().get("sites");
        {
            final String rightsRequest = """
                    {
                      "id": "",
                      "comment": "Un commentaire",
                      "fields": {
                        "organization": "INRAE",
                        "project": "openAdom",
                        "startDate": "10/10/1010",
                        "startDate": "10/11/1010",
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
                                  "fromDay": [
                                    1984,
                                    1,
                                    1
                                  ],
                                  "toDay": [
                                    1984,
                                    1,
                                    6
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                    """;

            String response = mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rightsRequest)
                            .cookie(authCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rightsRequest)
                            .cookie(lambdaCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

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

            mockMvc.perform((get("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .param("params", json)
                            .cookie(lambdaCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        String response;
        try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);

            mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'monsore'"))
                    .andReturn().getResponse().getContentAsString();
        }
        try (final InputStream refStream = getClass().getResourceAsStream(sites)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", sites, "text/plain", refStream);

            mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'monsore'"))
                    .andReturn().getResponse().getContentAsString();
        }

        String referencesRight = getJsonRightForAll(withRigthsUserId, List.of(List.of("sites", "publication"), List.of("type_de_sites", "publication")));
        referencesRight = JsonPath.parse(referencesRight).read("authorizationId");

        mockMvc.perform(get("/api/v1/applications/monsore/authorization/user/{userId}", withRigthsUserId)
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.userAuthorization.type_de_sites[0].operationTypes", hasItems("publication", "depot", "extraction")))
                .andExpect(jsonPath("$.userAuthorization.sites[0].operationTypes", hasItems("publication", "depot", "extraction")))
                .andExpect(jsonPath("$.userAuthorization.type_de_sites[0].requiredAuthorizations").isEmpty())
                .andExpect(jsonPath("$.userAuthorization.sites[0].requiredAuthorizations").isEmpty())
                .andExpect(jsonPath("$.applicationName").value("monsore"))
                .andExpect(jsonPath("$.applicationCreator").value(false))
                .andExpect(jsonPath("$.applicationManager").value(false))
                .andExpect(jsonPath("$.userManager").value(false))
                .andExpect(jsonPath("$.applicationUser").value(false))
                .andExpect(jsonPath("$.activeApplicationUser").value(false))
                .andExpect(jsonPath("$.publicAuthorization").isEmpty())
                .andReturn()
                .getResponse().getContentAsString();

        try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);

            response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }

        try (final InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);

            response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", "type_de_sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }
        //rechercher les lignes
        List<String> naturalKeys = new LinkedList<>(), hierarchicalKeys = new LinkedList<>(), ids = new LinkedList<>();
        mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites")
                        .cookie(withRigthsCookie))
                .andDo(result -> {
                    String contentAsString = result.getResponse().getContentAsString();
                    String[] read1 = JsonPath.parse(contentAsString).read("$.rows[*].naturalKey", String[].class);
                    naturalKeys.addAll(Arrays.asList(read1));
                    System.out.println(naturalKeys);
                    CollectionUtils.isEqualCollection(naturalKeys, List.of("bassin_versant", "plateforme"));

                    read1 = JsonPath.parse(contentAsString).read("$.rows[*].hierarchicalKey", String[].class);
                    hierarchicalKeys.addAll(Arrays.asList(read1));
                    System.out.println(hierarchicalKeys);
                    CollectionUtils.isEqualCollection(hierarchicalKeys, List.of("type_de_sitesKbassin_versant", "type_de_sitesKplateforme"));

                    read1 = JsonPath.parse(contentAsString).read("$.rows[*].rowId[*]", String[].class);
                    ids.addAll(Arrays.asList(read1));
                    Assertions.assertEquals(2, read1.length);
                });
        // recherche sur un critère
        response = mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites")
                        .param("tze_nom_en", "Platform")
                        .param("downloadDatasetQuery", """
                                {
                                       "componentFilters": [
                                                 {
                                                   "componentKey": "tze_nom_en",
                                                   "filters": ["Platform", "titi"]
                                                 }
                                      ]
                                    }""")
                        .cookie(withRigthsCookie))
                .andExpect(jsonPath("$.rows.length()", equalTo(1)))
                .andExpect(jsonPath("$.rows[0].values.tze_nom_fr", equalTo("Plateforme")))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertFalse(response.contains("tze_nom_en"));


        // recherche d'une ligne
        mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites")
                        .param("downloadDatasetQuery", SELECT_ROW_BY_ID.formatted(ids.get(1)))
                        .cookie(withRigthsCookie))
                .andExpect(jsonPath("$.rows[0].rowId[0]", equalTo(ids.get(1))));

        mockMvc.perform(get("/api/v1/applications/monsore/data/{dataName}/json", "type_de_sites")
                        .param("downloadDatasetQuery", SELECT_ROW_BY_NATURAL_KEY.formatted(hierarchicalKeys.get(1)))
                        .cookie(withRigthsCookie))
                .andExpect(jsonPath("$.rows[0].hierarchicalKey", equalTo(hierarchicalKeys.get(1))));
        String deletedIds = mockMvc.perform(delete("/api/v1/applications/monsore/data/{data}", "type_de_sites")
                        .param("downloadDatasetQuery", SELECT_ROW_BY_ID.formatted(ids.get(1)))
                        .cookie(withRigthsCookie))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(deletedIds.contains(ids.get(1)));

        //suppression par id
        mockMvc.perform(delete("/api/v1/applications/monsore/data/{refType}", "type_de_sites")
                        .param("_row_id_", ids.get(1))
                        .cookie(withRigthsCookie))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertTrue(true);

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getMonsoreReferentielFiles().entrySet()) {
            if ("pem".equals(e.getKey())) {
                continue;
            }
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

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
        final String additionalfileUUID;
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
            mockMvc.perform((multipart("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .file(addFile)
                            .param("params", json)
                            .cookie(authCookie)))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .cookie(authCookie))
                    .andExpect(jsonPath("$.users[*].label", contains("_public_",
                            "lambda",
                            "poussin",
                            "withrigths")))
                    .andExpect(jsonPath("$.additionalFileName", is("fichiers")))
                    .andExpect(jsonPath("$.additionalBinaryFiles[0].additionalBinaryFileForm.age", is("10")));

            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.users[*].label", contains("_public_",
                            "lambda",
                            "poussin",
                            "withrigths")))
                    .andExpect(jsonPath("$.additionalFileName", is("fichiers")))
                    .andExpect(jsonPath("$.additionalBinaryFiles[0].additionalBinaryFileForm.age", is("10")));

            final String error = Objects.requireNonNull(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResolvedException()).getMessage();
            Assertions.assertEquals("application inconnue 'monsore'", error);
            //pas de droits
            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                            .param("nameOrId", "monsore")
                            .param("params", additionalJsonRequest)
                            .cookie(lambdaCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(result -> Assertions.assertTrue(result.getResponse().getContentAsByteArray().length < 40, "empty data expected"));


            Assertions.assertEquals("application inconnue 'monsore'", error);
            //avec droits
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                                    .param("nameOrId", "monsore")
                                    .param("params", additionalJsonRequest)
                                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                                    .cookie(authCookie))
                            .andExpect(status().is2xxSuccessful())
                            .andExpect(request().asyncStarted())
                            .andReturn()))
                    .andExpect(result -> {
                        final List<ZipEntry> entries = new ArrayList<>();
                        try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {

                            ZipEntry zipEntry;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        }
                        List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .toList();
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere_infos.txt"), String.format("Le zip doit contenir %s", "monsoere_infos.txt"));
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere.yaml"), String.format("Le zip doit contenir %s", "monsoere.yaml"));
                    });
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                                    .param("nameOrId", "monsore")
                                    .param("params", """
                                            {
                                              "uuids": null,
                                              "fileNames": null,
                                              "additionalFilesInfos": {
                                                "fichiers": {
                                                  "fieldFilters": []
                                                }
                                              }
                                            }""")
                                    .cookie(withRigthsCookie)
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                            .andExpect(request().asyncStarted())
                            .andReturn()))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(result -> {
                        final List<ZipEntry> entries = new ArrayList<>();
                        try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {

                            ZipEntry zipEntry;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        }
                        System.out.println();
                        List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .collect(Collectors.toList());
                        System.out.println(entryNames);
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere_infos.txt"), String.format("Le zip doit contenir %s", "monsoere_infos.txt"));
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere.yaml"), String.format("Le zip doit contenir %s", "monsoere.yaml"));
                    });
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                                    .param("nameOrId", "monsore")
                                    .param("params", """
                                            {
                                              "uuids": null,
                                              "fileNames": null,
                                              "additionalFilesInfos": {
                                                "fichiers": {
                                                  "fieldFilters": []
                                                }
                                              }
                                            }""")
                                    .cookie(authCookie)
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                            .andExpect(request().asyncStarted())
                            .andExpect(request().asyncStarted())
                            .andReturn()))
                    .andExpect(status().is2xxSuccessful()).andExpect(result -> {
                        final List<ZipEntry> entries = new ArrayList<>();
                        try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {

                            ZipEntry zipEntry;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        }
                        List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .toList();
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere_infos.txt"), String.format("Le zip doit contenir %s", "monsoere_infos.txt"));
                        Assertions.assertTrue(() -> entryNames.contains("fichiers/monsoere/monsoere.yaml"), String.format("Le zip doit contenir %s", "monsoere.yaml"));
                    });

        }

        ResultActions typeDeFichiers = mockMvc.perform(get("/api/v1/applications/monsore/data/{refType}/json", "type_de_fichiers")
                        .cookie(authCookie))
                .andExpect(jsonPath("$.rows", hasSize(0)));

        Exception dataTest = mockMvc.perform(get("/api/v1/applications/monsore/data/{dataType}/json", "test")
                        .cookie(authCookie))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message", equalTo("missingData")))
                .andExpect(jsonPath("$.params.dataName", equalTo("test")))
                .andExpect(jsonPath("$.params.application", equalTo("monsore")))
                .andReturn()
                .getResolvedException();
        Assertions.assertInstanceOf(SiOreIllegalArgumentException.class, dataTest);
        // ajout de data
        final String projet = "manche";
        final String plateforme = "plateforme";
        final String site = "oir";
        resource = getClass().getResource(Fixtures.getPemRepositoryDataResourceName(projet, site));

        /*if(true){
            return;
        }*/

        // on dépose 3 fois le même fichier sans le publier
        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);

            try {
                // sans droit dépôt impossible de déposer
                // en fait on n'a pas les droits de lecture sur projet
                response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                                .file(refFile)
                                .param("params", Fixtures.getPemRepositoryParams(projet, plateforme, site, false))
                                .cookie(withRigthsCookie))
                        .andExpect(status().is4xxClientError())
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(jsonPath("$.message", Is.is(NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER)))
                        .andReturn().getResponse().getContentAsString();
                log.debug(response);
            } catch (ServletException servletException) {
                SiOreAuthorizationRequestException cause = (SiOreAuthorizationRequestException) servletException.getCause();
                AuthorizationRequestException requestException = cause.getException();
                Assertions.assertEquals(AuthorizationRequestException.MISSING_REQUIRED_AUTHORIZATION, requestException);
                Assertions.assertEquals("projet_manche", ((Map<String, List<Ltree>>) cause.getParams().get("missingRequiredAuthorizations")).get("projet").getFirst().getSql());
            }

            String createRights = getJsonRightsforRestrictions(
                    withRigthsUserId,
                    List.of(OperationType.depot.name()),
                    "pem",
                    "type_de_sitesKplateforme.sitesKoir.sitesKoir__p1",
                    "1984,1,1",
                    "1984,1,5",
                    authCookie);

            //fileOrUUID.binaryFileDataset/applications/{name}/file/{id}
            for (int i = 0; i < 3; i++) {
                response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                                .file(refFile)
                                .param("params", Fixtures.getPemRepositoryParams(projet, plateforme, site, false))
                                .cookie(withRigthsCookie))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn().getResponse().getContentAsString();
                log.debug(response);
            }
            //on regarde les versions déposées
            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", Fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == false )]", hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(0)))
                    .andReturn().getResponse().getContentAsString();

            //récupération de l'identifiant de la dernière version déposée
            oirFilesUUID = JsonPath.parse(response).read("$[2].id");

            // on vérifie l'absence de data
            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                            .cookie(authCookie))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows", hasSize(0)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);

            // on publie le dernier fichier déposé sans les droits

            Exception exception = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .param("params", Fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, true))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResolvedException();

            Assertions.assertInstanceOf(NotApplicationDataWriterException.class, exception);
            Assertions.assertEquals(NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER, exception.getMessage());
            Assertions.assertEquals("pem", ((NotApplicationDataWriterException) exception).dataName);
            Assertions.assertEquals("monsore", ((NotApplicationDataWriterException) exception).applicationName);


            // on donne les droits publication


            getJsonRightsforRestrictions(withRigthsUserId, List.of(OperationType.publication.name()),
                    "pem", "type_de_sitesKplateforme.sitesKoir.sitesKoir__p1", "1984,1,1", "1984,1,6", authCookie);


            // on publie le dernier fichier déposé

            response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .param("params", Fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, true))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            // on récupère la liste des versions déposées

            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", Fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == false )]", hasSize(2)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(1)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(oirFilesUUID))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            // on récupère le data en base

            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows", hasSize(34)))
                    //.andExpect(jsonPath("$.rows[*]", hasSize(34)))
                    .andExpect(jsonPath("$.rows[*].values[? (@.chemin == 'oir__p1' && @.projet == 'projet_manche')]", hasSize(34)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            final byte[] responseToByteArray = mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/data/pem/zip")
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                    .cookie(authCookie))
                            .andExpect(status().is2xxSuccessful())
                            .andExpect(request().asyncStarted())
                            .andReturn()
                    ))
                    .andDo(result -> {
                        if (result.getResponse().getStatus() != 200) {
                            log.info(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(testZip(List.of(
                            "pem.csv",
                            "references/especes.csv",
                            "references/type_de_sites.csv",
                            "references/unites.csv",
                            "references/projet.csv",
                            "references/valeurs_qualitatives.csv",
                            "references/sites.csv"/*,
                            "additionalFiles/fichiers/monsoere/monsoere_infos.txt",
                            "additionalFiles/fichiers/monsoere/monsoere.yaml"*/)))
                    .andReturn().getResponse().getContentAsByteArray();
        }
        //on publie 4 fichiers

        publishOrDepublish(authCookie, "manche", "plateforme", "scarff", 68, true, 1, true);
        publishOrDepublish(authCookie, "atlantique", "plateforme", "scarff", 34, true, 1, true);
        publishOrDepublish(authCookie, "atlantique", "plateforme", "nivelle", 34, true, 1, true);
        publishOrDepublish(authCookie, "manche", "plateforme", "nivelle", 34, true, 1, true);
        //on publie une autre version
        final String fileUUID = publishOrDepublish(authCookie, "manche", "plateforme", "nivelle", 34, true, 2, true);
        // on supprime l'application publiée
        response = mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(response, fileUUID);
        log.debug(StringUtils.abbreviate(response, 50));
        try {
            publishOrDepublish(withRigthsCookie, "manche", "plateforme", "nivelle", 34, true, 1, true);

        } catch (final NotApplicationDataWriterForDepositException e) {
            Assertions.assertEquals(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT, e.getMessage());
            Assertions.assertEquals("pem", e.dataName);
        }
        getJsonRightsforRestrictions(withRigthsUserId, List.of(OperationType.publication.name()),
                "pem", "type_de_sitesKplateforme.sitesKnivelle.sitesKnivelle__p1", "1984,1,1", "1984,1,6", authCookie);

        //les droit s de publication permettent aussi le dépôt
        String fileUUID2 = publishOrDepublish(withRigthsCookie, "manche", "plateforme", "nivelle", 34, true, 2, true);

        testFilesAndDataOnServer(plateforme, "manche", "nivelle", 0, 2, fileUUID2, true);


        // on depublie le fichier oir déposé (les droits publication valent dépublication

        response = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                        .param("params", Fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, false))
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère la liste des versions déposées

        response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                        .param("repositoryId", Fixtures.getPemRepositoryId(plateforme, projet, site))
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == false )]", hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère le data en base si j'ai les droits de publication je peux aussi lire les données avec ces droits (seuelement ^pour nivelle

        mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='nivelle__p1' && @.projet == 'projet_manche')].chemin", hasSize(34)))

                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='scarff__p1' && @.projet == 'projet_manche')].chemin", hasSize(34)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='oir__p1')].chemin", hasSize(0)))
                .andExpect(jsonPath("$.rows.length()").value(136))
                .andExpect(jsonPath("$.rows[*]", hasSize(136)))
                .andReturn().getResponse().getContentAsString();

        //pour le createur auth on a les fichiers de scarff
        mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='nivelle__p1' && @.projet == 'projet_manche')].chemin", hasSize(34)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='scarff__p1' && @.projet == 'projet_manche')].chemin", hasSize(34)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='oir__p1')].chemin", hasSize(0)))
                .andExpect(jsonPath("$.rows.length()").value(136))
                .andExpect(jsonPath("$.rows[*]", hasSize(136)))
                .andReturn().getResponse().getContentAsString();

        response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem/json")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='scarff__p1')].chemin", hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='scarff__p1')].chemin", hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='nivelle__p1')].chemin", hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values[?(@.chemin=='oir__p1')].chemin", hasSize(0)))
                .andExpect(jsonPath("$.rows.length()").value(136))
                .andExpect(jsonPath("$.rows[*]", hasSize(136)))
                .andExpect(jsonPath("$.rows[*].values[? (@.site.chemin == 'oir__p1')][? (@.projet.value == 'projet_manche')]", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));
        // on supprime le fichier on peut dépublier mais pas supprimer le fichier
        NotApplicationCanDeleteRightsException resolvedException = (NotApplicationCanDeleteRightsException) mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID2)
                        .cookie(withRigthsCookie))
                .andExpect(status().is4xxClientError())
                .andReturn()
                .getResolvedException();
        assert resolvedException != null;
        Assertions.assertEquals("NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION", resolvedException.getMessage());
        Assertions.assertEquals("pem", resolvedException.getDataType());
        Assertions.assertEquals("monsore", resolvedException.getApplicationName());
        /*Exception resolvedException1 = mockMvc.perform(delete("/api/v1/applications/monsore/data/pem")
                        .cookie(authCookie))
                .andExpect(status().is4xxClientError())
                .andReturn()
                .getResolvedException();
        Assertions.assertInstanceOf(NotApplicationCanDeleteRightsException.class, resolvedException1);*/

        //on donne les droits de suppression

        getJsonRightsforRestrictions(withRigthsUserId, List.of(OperationType.delete.name()),
                "pem", "type_de_sitesKplateforme.sitesKnivelle.sitesKnivelle__p1", "1984,1,1", "1984,1,6", authCookie);

        // on supprime le fichier a les droits car à les droits de publication
        mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID2)
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    //@Tag("app.teledetection")
    @Disabled
    public void addApplicationTeledetection() throws Exception {
        final URL resource = getClass().getResource(Fixtures.getTeledetectionConfigurationResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "teledec.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "teledec");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "teledec", ""));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        Fixtures.getTeledetectionReferencesFiles()
                .forEach((refName, refPath) -> {
                    String refName1 = refName.replaceAll("(.*)2$", "$1");
                    try (final InputStream refStream = getClass().getResourceAsStream(refPath)) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", refName1, "text/plain", refStream);

                        mockMvc.perform(multipart("/api/v1/applications/teledec/data/{refType}", refName1)
                                        .file(refFile)
                                        .cookie(authCookie))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                                .andReturn().getResponse().getContentAsString();
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        final Matcher<List> m = new Matcher<>() {
            @Override
            public boolean matches(final Object o) {
                final Map<String, List<String>> expected = new LinkedHashMap<>();
                for (final List<String> el : (ArrayList<ArrayList<String>>) o) {
                    for (final String s : el) {
                        expected
                                .computeIfAbsent(s.split("__")[0], k -> new LinkedList<>())
                                .add(s);
                    }
                }
                Assertions.assertEquals(7, expected.get("ndvi_s2_max_10m").size(), () -> "expected  %s in %s ".formatted(3, "ndvi_s2_max_10m"));
                Assertions.assertEquals(7, expected.get("ndvi_s2_mean_10m").size(), () -> "expected  %s in %s ".formatted(3, "ndvi_s2_mean_10m"));
                Assertions.assertEquals(7, expected.get("ndvi_s2_min_10m").size(), () -> "expected  %s in %s ".formatted(3, "ndvi_s2_min_10m"));
                Assertions.assertEquals(7, expected.get("ndvi_s2_sd_10m").size(), () -> "expected  %s in %s ".formatted(3, "ndvi_s2_sd_10m"));
                return true;
            }

            @Override
            public void describeMismatch(final Object o, final Description description) {
                log.info("ok");
            }

            @Override
            public void _dont_implement_Matcher___instead_extend_BaseMatcher_() {

            }

            @Override
            public void describeTo(final Description description) {

            }
        };
        mockMvc.perform(get("/api/v1/applications/teledec/data/{refType}", "tr_variable_local_vloc")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceValues[*].values.vloc_metadata").value(m));

        Fixtures.getTeledetectionDataFiles()
                .forEach((dataName, dataPath) -> {
                    try (final InputStream dataStream = getClass().getResourceAsStream(dataPath)) {
                        final MockMultipartFile dataFile = new MockMultipartFile("file", dataName, "text/plain", dataStream);

                        mockMvc.perform(multipart("/api/v1/applications/teledec/data/{data}", dataName)
                                        .file(dataFile)
                                        .cookie(authCookie))
                                .andDo(result -> {
                                    if (result.getResponse().getStatus() != 201) {
                                        log.info(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                    }
                                })
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.fileId", IsNull.notNullValue()))
                                .andReturn().getResponse().getContentAsString();
                    } catch (final Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        log.info("fini!");

    }


    private String[] getApplicationsFlux(final Cookie cookie, final String... filter) throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications")
                                .accept(MediaType.APPLICATION_NDJSON_VALUE)
                                .cookie(cookie)
                                .param("filter", filter))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(request().asyncStarted())
                        .andReturn()))
                .andReturn().getResponse().getContentAsString()
                .split("\n");
    }

    private String getJsonRightsforRestrictions(final String withRigthsUserId, final List<String> roles,
                                                final String datatype,
                                                final String localization,
                                                final String from, final String to,
                                                Cookie authenticateCookie) throws Exception {
        Cookie authenticateCookie1 = authenticateCookie == null ? authCookie : authenticateCookie;
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
                                   "fromDay":[%3$s],
                                   "toDay":[%4$s]
                                }
                           }
                      }
                }""", datatype, localization, from, to, String.join("\",\"", roles), withRigthsUserId, System.currentTimeMillis());
        MockHttpServletRequestBuilder createRight = post("/api/v1/applications/monsore/authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authenticateCookie1)
                .content(json);
        return mockMvc.perform(createRight)
                .andDo(result -> {
                    final int status = result.getResponse().getStatus();
                    if (status > 300) {
                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                    }
                })
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String getJsonRightForAll(final String withRigthsUserId, List<List<String>> dataNameAndRoles) throws
            Exception {
        List<String> formattedStrings = dataNameAndRoles.stream()
                .filter(subList -> !subList.isEmpty())
                .map(subList -> {
                    String dataname = subList.getFirst();
                    List<String> roles = subList.subList(1, subList.size());
                    String rolesString = roles.stream().collect(Collectors.joining("\" ,\"", "\"", "\""));
                    return String.format("\"%s\": [%s]", dataname, rolesString);
                })
                .collect(Collectors.toList());

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
                        }""",
                withRigthsUserId,
                authorizationForAll
        );
        MockHttpServletRequestBuilder createRight = multipart("/api/v1/applications/monsore/authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie)
                .content(json);
        return mockMvc.perform(createRight)
                .andDo(result -> {
                    final int status = result.getResponse().getStatus();
                    if (status > 300) {
                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                    }
                })
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String setJsonRightsForMonsoere(final Cookie cookie, final String withRigthsUserId, final String role, final String datatype) throws
            Exception {
        String json = String.format("""
                {
                   "usersId":["%3$s"],
                   "applicationNameOrId":"monsore",
                   "id": null,
                   "name": "une submissionScope sur monsore",
                   "dataName":"pem",
                   "authorizations":{
                      "%2$s":{
                        "%1$s":[
                               {
                               "requiredAuthorizations": {
                                  "projet": "projet_atlantique"
                                },
                              "fromDay": null,
                               "toDay": null
                             },
                            {
                             "dataGroups": [],
                              "requiredAuthorizations": {
                                "projet": "projetKprojet_manche"
                                },
                               "fromDay": null,
                               "toDay": null
                             }
                        ]
                      }
                   }
                }""", role, datatype, withRigthsUserId);
        MockHttpServletRequestBuilder createRight = post("/api/v1/applications/monsore/authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookie)
                .content(json);
        return mockMvc.perform(createRight)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String publishOrDepublish(final Cookie cookie, final String projet, final String plateforme, final String site, final int expected,
                                      final boolean toPublish, final int numberOfVersions, final boolean published) throws Exception {
        final URL resource;
        String response;
        resource = getClass().getResource(Fixtures.getPemRepositoryDataResourceName(projet, site));
        try (final InputStream refStream = Objects.requireNonNull(resource).openStream()) {

            //dépôt et publication d'un fichier projet site__p1
            final MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
            refFile.transferTo(Path.of("/tmp/pem.csv"));
            MvcResult mockResponse = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .param("params", Fixtures.getPemRepositoryParams(
                                    projet,
                                    plateforme,
                                    site,
                                    toPublish))
                            .cookie(cookie))
                    .andReturn();
            if (mockResponse.getResponse().getStatus() >= 200 && mockResponse.getResponse().getStatus() < 300) {
                final String fileUUID = JsonPath.parse(mockResponse.getResponse().getContentAsString()).read("$.id");
                testFilesAndDataOnServer(plateforme, projet, site, expected, numberOfVersions, fileUUID, published);
                log.debug(StringUtils.abbreviate(mockResponse.getResponse().getContentAsString(), 50));
                return fileUUID;
            }
            throw Objects.requireNonNull(mockResponse.getResolvedException());
        }
    }

    /**
     * This is a case where a datatype has no submissionScope section.
     * The only authorizations that can be put on are on none or all values.
     */
    @Test
    @Tag("core.config")
    @Disabled
    public void testProgressiveYamlWithoutAuthorization() throws Exception {
        final String authorizationId;
        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("yamlWithoutAuthorization"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "progressive", ""));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        //pas de referentiels
        progressiveYamlAddData();

        final String lambdaUserId = lambdaUser.userId().toString();
        final Cookie readerCookies = mockMvc.perform(post("/api/v1/login")
                        .param("login", "lambda")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);


        {

            Assertions.assertEquals(1, Arrays.stream(getApplicationsFlux(readerCookies, "ALL"))

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "progressive".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count());
        }

        {
            mockMvc.perform(get("/api/v1/applications/progressive/data/date_de_visite/json")
                            .cookie(readerCookies)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
            final String json = String.format("""
                    {
                       "usersId":["%1$s"],
                       "applicationNameOrId":"progressive",
                       "id": null,
                       "name": "une submissionScope sur progressive",
                       "dataName":"date_de_visite",
                       "authorizations":{
                       "%2$s":{
                       "extraction":[
                          {
                             "requiredAuthorizations":{},
                             "dataGroup":[],
                             "timeScope":{
                                "fromDay":null,
                                "toDay":null
                             }
                          }
                       ]
                    }
                    }
                    }""", lambdaUserId, "date_de_visite");


            final MockHttpServletRequestBuilder create = post("/api/v1/applications/progressive/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            final String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            authorizationId = JsonPath.parse(response).read("$.authorizationId", String.class);
            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            // Une fois l'accès donné, on doit pouvoir avec l'application dans la liste"
            Assertions.assertEquals(1, Arrays.stream(getApplicationsFlux(readerCookies, "ALL"))

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "progressive".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count());
        }

        {
            final String json = mockMvc.perform(get("/api/v1/applications/progressive/data/date_de_visite/json")
                            .cookie(readerCookies)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.relevant.numero").value(hasItemInArray(equalTo("125")), String[].class))
                    .andReturn().getResponse().getContentAsString();
        }
        final MockHttpServletRequestBuilder delete = delete(String.format("/api/v1/applications/progressive/authorization/%s", authorizationId))
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie);
        mockMvc.perform(delete)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        // L'utilisateur sans droit ne peut voir les applications

        //TODO


    }

    @Test
    @Disabled
    @Tag("core.config")
    public void testProgressiveYamlWithEmptyDatagroup() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("yamlWithEmptyDatagroup"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "progressive", ""));

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        progressiveYamlAddReferences();
        progressiveYamlAddData();
    }

    /**
     * Test that a localisationScope referes to a variable component with computationChecker reference
     */
    @Test
    @Disabled
    @Tag("core.config")
    public void testProgressiveYamlWithNoReference() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("testAuthorizationScopeWithoutReference"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final BadApplicationConfigurationException exception = (BadApplicationConfigurationException)
                    fixtures.loadApplicationWithError(configuration, authCookie, "progressive");
            assert exception != null;
            Assertions.fail("refaire le test san configuration parding result");
            //ValidationCheckResult validationCheckResult = exception.getConfigurationParsingResult().validationCheckResults()
            //        .get(0);
            //Assertions.assertEquals("authorizationScopeMissingReferenceCheckerForAuthorizationScope", validationCheckResult.message());
            // Map<String, Object> messageParams = validationCheckResult.messageParams();
            // Assertions.assertEquals("localization", messageParams.get("authorizationScopeName"));
            // Assertions.assertEquals("date_de_visite", messageParams.get("dataName"));
            // Assertions.assertEquals("agroecosysteme", messageParams.get("component"));
            // Assertions.assertEquals("localisation", messageParams.get("variable"));
        }
    }

    @Test
    @Disabled
    @Tag("core.config")
    public void testProgressiveYamlWithoutAuthorizationScope() {

        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("testProgressiveYamlWithoutAuthorizationScope"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "progressive", ""));

            //pas de référentiel
            progressiveYamlAddData();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    @Tag("core.config")
    public void testProgressiveYamlWithoutTimescopeScope() {

        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("testProgressiveYamlWithoutTimescopeScope"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "progressive", ""));


            progressiveYamlAddReferences();
            progressiveYamlAddData();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A referenceScopes that refers to a component variable that is not declared as a composite reference
     */
    @Test
    @Disabled
    @Tag("core.config")
    public void testProgressiveWithReferenceAndNoHierarchicalReferenceYaml() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getProgressiveYaml().get("testAuthorizationScopeWithReferenceAndNoHierarchicalReference"));
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "progressive", ""));

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
        progressiveYamlAddReferences();
        progressiveYamlAddData();
    }

    private void progressiveYamlAddReferences() throws Exception {
        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getProgressiveYamlReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    private void progressiveYamlAddData() throws Exception {
        String response;
        for (final Map.Entry<String, String> e : Fixtures.getProgressiveYamlDataFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.fileId", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();
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
            addUserRightCreateApplication(authUserId, "recursivite");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "recursivite", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/recursivite")
                            .param("filter", "ALL")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.type", IsEqual.equalTo("DynamicComponent")))
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon")))
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_")))
                    .andExpect(jsonPath("$.internationalization.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Taxa properties")))
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/recursivite/data/{refType}/json", "taxon")
                            .cookie(authCookie))
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

                    response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                    .file(refFile)
                                    .cookie(authCookie))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                            .andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        }
        for (final Map.Entry<String, String> e : Fixtures.getRecursiviteReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

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
            addUserRightCreateApplication(authUserId, "pattern");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "pattern", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/pattern")
                            .param("filter", "ALL")
                            .cookie(authCookie))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.type", IsEqual.equalTo("DynamicComponent")))
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.reference", IsEqual.equalTo("proprietes_taxon")))
                    .andExpect(jsonPath("$.data.taxon.componentDescriptions.proprietesDeTaxon.prefix", IsEqual.equalTo("pt_")))
                    .andExpect(jsonPath("$.internationalization.data.taxon.components.proprietesDeTaxon.exportHeader.title.en", IsEqual.equalTo("Properties of Taxa")))
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/pattern/data/{refType}/json", "taxon")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$..values.tel_S2_value[*].__VALUE__", containsInAnyOrder("7.2", "3.4", "2.1", "2.6", "2.5", "5.2", "3.9", "3.2", "1.2")))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_resolution", containsInAnyOrder(3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2, 3.2)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_qualifier", containsInAnyOrder(3, 3, 3, 3, 3, 3, 3, 3, 3)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].tel_S2_variable", containsInAnyOrder("annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy", "annecy")))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].swc_qc", containsInAnyOrder(1, 1, 0, 2, 1, 0, 0, 1, 1)))
                    .andExpect(jsonPath("$..values.tel_S2_value[*].swc_sd", containsInAnyOrder(3.9, 2.5, 7.2, 3.2, 2.1, 3.4, 1.2, 5.2, 3.9)))
                    .andExpect(jsonPath("$.rows[*].refsLinkedTo.site['tel_S2_value::tel_S2_variable::annecy_S2_3_3.2'].hierarchicalKey.sql", containsInAnyOrder("siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy", "siteKannecy")))
                    .andExpect(jsonPath("$.referenceTypeForReferencingColumns['tel_S2_value::tel_S2_variable']", Is.is("site")))
                    .andReturn().getResponse().getContentAsString();

        }
        {
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/pattern/data/taxon/zip")
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                    .cookie(authCookie))
                            .andExpect(status().is2xxSuccessful())
                            .andExpect(request().asyncStarted())
                            .andReturn()
                    ))


                    .andExpect(testZip(List.of("taxon.csv", "references/proprietes_taxon.csv", "references/site.csv")
                    ));

        }
        // Ajout de taxon
        {
            for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
                try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey())
                                    .file(refFile)
                                    .cookie(authCookie))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                            .andReturn().getResponse().getContentAsString();

                    JsonPath.parse(response).read("$.id");
                }
            }
        }
        for (final Map.Entry<String, String> e : Fixtures.getPatternReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/pattern/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    @Test
    @Tag("SUITE")
    @Tag("core.config")
    public void testComputedWithNaturalKeyColumns() throws Exception {

        final URL resource = getClass().getResource(Fixtures.getComputedWithNaturalKeyColumns());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "computedWithNaturalKeyColumns.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "computedwithnaturalkeycolumns");
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "computedwithnaturalkeycolumns", ""));
            final String response = mockMvc.perform(get("/api/v1/applications/computedwithnaturalkeycolumns")
                            .param("filter", "ALL")
                            .cookie(authCookie))
                    .andReturn().getResponse().getContentAsString();

        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getDataComputedWithNaturalKeyColumns().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(multipart("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        {
            mockMvc.perform(get("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}/json", "site_sit")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows[*].values.site_natural_key", contains(
                            "type_de_site1__site1",
                            "type_de_site1__site2",
                            "type_de_site2__site1",
                            "type_de_site2__site2"))
                    )
                    .andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4)))
                    .andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1")))
                    .andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2")))
                    .andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains("")))
                    .andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains("")))
                    .andExpect(jsonPath("$.rows[0].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1)))
                    .andExpect(jsonPath("$.rows[0].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1)))
                    .andExpect(jsonPath("$.rows[1].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1)))
                    .andExpect(jsonPath("$.rows[1].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1)))
                    .andExpect(jsonPath("$.rows[2].refsLinkedTo.site_sit.keys()", hasSize(1)))
                    .andExpect(jsonPath("$.rows[2].refsLinkedTo.type_site_tsi.keys()", hasSize(1)))
                    .andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4)))
                    .andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1")))
                    .andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2")))
                    .andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains("")))
                    .andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains("")))
                    //.andExpect(jsonPath("$.rows[0].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[0].refsLinkedTo.type_site_tsi.tsi_noms", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[1].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(2))) //TODO
                    //.andExpect(jsonPath("$.rows[1].refsLinkedTo.type_site_tsi.tsi_noms", hasSize(2))) //TODO
                    .andExpect(jsonPath("$.rows[2].refsLinkedTo.site_sit.keys()", hasSize(1)))
                    .andExpect(jsonPath("$.rows[2].refsLinkedTo.type_site_tsi.keys()", hasSize(1)));
        }
    }

    private void testFilesAndDataOnServer(final String plateforme, final String projet, final String site, final int expected,
                                          final int numberOfVersions, final String fileUUID, final boolean published) throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                        .param("repositoryId", Fixtures.getPemRepositoryId(plateforme, projet, site))
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(numberOfVersions)));

        if (published) {
            resultActions = resultActions.
                    andExpect(jsonPath("$[*][?(@.params.published == true )]", hasSize(1)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(fileUUID));
        } else {
            resultActions = resultActions.
                    andExpect(jsonPath("$[*][?(@.params.published == true )]").isEmpty());
        }
        resultActions
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Tag("app.acbb")
    public void addApplicationAcbb() throws Exception {
        addUserRightCreateApplication(authUserId, "acbb");
        final URL resource = getClass().getResource(Fixtures.getAcbbApplicationConfigurationResourceName());
        assert resource != null;
        try (final InputStream in = resource.openStream()) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", in);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "acbb_openadom_v2", ""));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        addReferences();
        addDataFluxTours();
        //addDataSWC();
        //addDataBiomassProduction();
    }

    private void addUserRightCreateApplication(final UUID userId, final String pattern) throws Exception {
        final ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                        .param("userIdOrLogin", userId.toString())
                        .param("applicationPattern", pattern)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                //.andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(userId.toString())))
                .andExpect(jsonPath("$.roles.memberOf", hasItem("applicationCreator")))
                .andExpect(jsonPath("$.authorizations", hasItem(pattern)))
                .andExpect(jsonPath("$.id", IsEqual.equalTo(userId.toString())));

    }

    private void addDataSWC() throws Exception {
        try (final InputStream in = fixtures.openSwcDataResourceName(true)) {
            final MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);

            final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            final String actualJson = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/SWC/json")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
            Assertions.assertEquals(2912, StringUtils.countMatches(actualJson, "\"SWC\":"));
        }

        {
            final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/SWC/zip")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            Objects.requireNonNull(mvcResult.getRequest().getAsyncContext()).setTimeout(120000);
            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(testZip(List.of("SWC.csv")));
        }
    }

    private void addDataBiomassProduction() throws Exception {
        // ajout de data
        try (final InputStream in = getClass().getResourceAsStream(Fixtures.getBiomasseProductionTeneurDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);

            final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            final String actualJson = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur/json")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
            Assertions.assertEquals(252, StringUtils.countMatches(actualJson, "prairie permanente"));
        }

        {
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur/zip")
                                    .cookie(authCookie)
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                            .andExpect(request().asyncStarted())
                            .andExpect(status().is2xxSuccessful())
                            .andReturn()))
                    .andExpect(testZip(List.of("biomasse_production_teneur.csv")));
        }
    }

    private void addDataFluxTours() throws Exception {
        // ajout de data
        try (final InputStream in = getClass().getResourceAsStream(Fixtures.getFluxToursDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);

            final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx")
                            .file(file)
                            .param("params", """
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
                            .cookie(authCookie))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        // restitution de data json
        {
//            String expectedJson = Resources.toString(getClass().getResource("/data/acbb_openadom_v2/compare/export.json"), StandardCharsets.UTF_8);
            final String actualJson = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/json")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].[? (@.values.flx_day =~ /^.*date:2004.*$/)]", hasSize(17568)))
                    .andExpect(jsonPath("$.rows[*]", hasSize(17568)))
//                    .andExpect(content().json(expectedJson))
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
        }

        // restitution de data csv
        {
            final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/zip")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(request().asyncStarted())
                    .andExpect(status().isOk())
                    .andReturn();
            Objects.requireNonNull(mvcResult.getRequest().getAsyncContext()).setTimeout(120000);

            mockMvc.perform(asyncDispatch(
                            mvcResult
                    ))
                    .andExpect(testZip(List.of("t_flux_tours_flx.csv")));
        }
    }

    private void addReferences() throws Exception {
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getAcbbReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andDo(result -> {
                            final int status = result.getResponse().getStatus();
                            if (status > 300) {
                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                            }
                        })
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        final String getReferenceResponse = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/tr_parcelles_par/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authCookie))
                .andDo(result -> {
                    final int status = result.getResponse().getStatus();
                    if (status > 300) {
                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                    }
                })
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rows", hasSize(103)))
                //.andExpect(jsonPath("$.totalRows", Is.is(103)))
                .andReturn().getResponse().getContentAsString();

        final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/tr_parcelles_par/csv")
                        .cookie(authCookie)
                        .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(result -> StringUtils.countMatches(result.getResponse().getContentAsString(), "theix"));
    }

    @Test
    @Tag("app.haute_frequence")
    @Disabled
    public void addApplicationHauteFrequence() throws Throwable {
        addUserRightCreateApplication(authUserId, "hautefrequence");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getHauteFrequenceApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "hautefrequence", ""));
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getHauteFrequenceReferentielFiles().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getHauteFrequenceDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @Tag("domain.model")
    @Disabled
    public void addDuplicatedTest() throws Throwable {
        addUserRightCreateApplication(authUserId, "duplicated");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getDuplicatedApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "duplicated.yaml", "text/plain", configurationFile);
            final String id = fixtures.getIdFromApplicationResult(fixtures.loadApplication(configuration, authCookie, "duplicated", ""));
        }
        String message;

        //on charge le fichier de type zone d'étude
        String typezonewithoutduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("typezonewithoutduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on recharge le fichier de type zone d'étude
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude2.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone type d'étude avec une duplication
        String typezonewithduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("typezonewithduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude_duplicate.csv", "text/plain", refStream);
            ResultActions error = mockMvc
                    .perform(multipart("/api/v1/applications/duplicated/data/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
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
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();
/*
on test le dépôt d'un fichier récursif
 */


//on charge le fichier de zone d'étude
        String zonewithoutduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("zonewithoutduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on recharge le fichier de zone d'étude
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude2.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        mockMvc.perform(get("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone d'étudeavec une duplication
        String zonewithduplicationDuplication = Fixtures.getDuplicatedReferentielFiles().get("zonewithduplication");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithduplicationDuplication)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_duplicated.csv", "text/plain", refStream);
            ResultActions error = mockMvc
                    .perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
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
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();

        //on charge le fichier de zone d'étudeavec une duplication
        String zonewithmissingParent = Fixtures.getDuplicatedReferentielFiles().get("zonewithmissingparent");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithmissingParent)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_missing_parent.csv", "text/plain", refStream);
            ResultActions error = mockMvc
                    .perform(multipart("/api/v1/applications/duplicated/data/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
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
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();

        //on teste un dépot de fichier de données
        String dataWithoutDuplicateds = Fixtures.getDuplicatedDataFiles().get("data_without_duplicateds");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        //on teste le nombre de ligne
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }

        // on  redepose le fichier

        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            String response = mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$[0].validationCheckResult.messageParams.file", IsEqual.equalTo("dty"))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.failingRowContent", StringContains.containsString("1980-02-23")))
                    .andReturn().getResponse().getContentAsString();
        }
        // le nombre de ligne est inchangé
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }
        //on teste un dépot de fichier de données avec lignes dupliquées
        String dataWithDuplicateds = Fixtures.getDuplicatedDataFiles().get("data_with_duplicateds");
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithDuplicateds)) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data_with_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$[0].validationCheckResult.message", IsEqual.equalTo("duplicatedLineInDatatype")))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(5)))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(6)))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.Date_day.value", hasItems(1980, 2, 24, 0, 0)))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.localization_zones_etudes.value.sql", IsEqual.equalTo("site1")));

        }
        // le nombre de ligne est inchangé
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty/json")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.rows", hasSize(4)))
                    //.andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }
    }

    @Test
    @Tag("app.olac")
    @Disabled
    public void addApplicationOLAC() throws Exception {
        addUserRightCreateApplication(authUserId, "olac");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getOlaApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/olac")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        String contentAsString = mockMvc.perform(get("/api/v1/applications/olac", "ALL,ReferenceType")
                        .cookie(authCookie)
                        .param("filter", "ALL"))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse().getContentAsString();

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getOlaReferentielFiles().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/olac/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getConditionPrelevementDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "condition_prelevements.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/condition_prelevements")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhysicoChimieDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "physico-chimie.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/physico-chimie")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getSondeDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "sonde_truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/sonde_truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());

        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhytoAggregatedDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton_aggregated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton_aggregated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getPhytoplanctonDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton__truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getZooplanctonDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton__truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getZooplactonBiovolumDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton_biovolumes.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton_biovolumes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @Tag("app.foret")
    @Disabled
    public void addApplicationFORET_essai() throws Exception {
        addUserRightCreateApplication(authUserId, "foret");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getForetEssaiApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "foret_essai.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/foret")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getForetEssaiReferentielFiles().entrySet()) {
            log.debug(e.getKey());
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        for (final Map.Entry<String, String> entry : Fixtures.getForetEssaiDataResourceName().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(entry.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/" + entry.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());

                if ("swc_j".equals(entry.getKey())) {
                    authenticationService.setRoleAdmin();
                    String responseForBuildSynthesis = mockMvc.perform(put("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .cookie(authCookie))
                            .andExpect(jsonPath("$.SWC", hasSize(8)))
                            .andReturn().getResponse().getContentAsString();
                    String responseForGetSynthesis = mockMvc.perform(get("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .cookie(authCookie))
                            .andExpect(jsonPath("$.SWC", hasSize(8)))
                            .andExpect(jsonPath("$.SWC[*].aggregation", containsInAnyOrder("10.0", "120.0", "160.0", "200.0", "250.0", "30.0", "55.0", "80.0")))
                            .andReturn().getResponse().getContentAsString();
                }
            }
        }
    }

    @Test
    @Tag("app.foret")
    @Disabled
    public void addApplicationFORET() throws Exception {
        addUserRightCreateApplication(authUserId, "foret");
        try (final InputStream configurationFile = fixtures.getClass().getResourceAsStream(Fixtures.getForetApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "foret.yaml", "text/plain", configurationFile);
            mockMvc.perform(multipart("/api/v1/applications/foret")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : Fixtures.getForetReferentielFiles().entrySet()) {
            try (final InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = fixtures.getClass().getResourceAsStream(Fixtures.getFluxMeteoForetDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    @Disabled("utile comme benchmark, ne vérifie rien")
    @Tag("integration.persistence")
    public void benchmarkImportData() throws Exception {
        addApplicationAcbb();
        try (final InputStream in = fixtures.openSwcDataResourceName(false)) {
            final MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);
            final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        }
    }

    @Test
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    @Disabled
    @Tag("integration.rest")
    public void testGetUploadBundle() throws Exception {
        URL resource = getClass().getResource(Fixtures.getMonsoreApplicationConfigurationWithRepositoryResourceName());
        try (final InputStream in = Objects.requireNonNull(resource).openStream()) {
            loadApplicationMonsoere(in);
        } catch (final Throwable e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    private byte[] getAndTestUploadBundleZip() throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsore/upload-bundle")
                                .accept(MediaType.APPLICATION_OCTET_STREAM)
                                .cookie(authCookie)) //appel du sevice zip
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(request().asyncStarted())
                        .andReturn()))
                .andExpect(result -> {
                    List<ZipEntry> entries = new ArrayList<>();
                    try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zi.getNextEntry()) != null) {
                            entries.add(zipEntry);
                            zi.readAllBytes();
                        }
                    }
                    final List<String> entryNames = entries.stream()
                            .map(ZipEntry::getName)
                            .toList();
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
                })
                .andReturn().getResponse().getContentAsByteArray();
    }
}
