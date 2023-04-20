package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OperationType;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCreatorRightsException;
import fr.inra.oresing.rest.exceptions.configuration.BadApplicationConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.Cookie;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class OreSiResourcesTest {

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

    private Cookie authCookie;

    private Cookie lambdaCookie;

    private UUID authUserId;
    private CreateUserResult lambdaUser;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private UserRepository userRepository;


    @Before
    public void createUser() throws Exception {
        lambdaUser = authenticationService.createUser("lambda", "xxxxxxxx");
        lambdaCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "lambda")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult authUser = authenticationService.createUser("poussin", "xxxxxxxx");
        authUserId = authUser.getUserId();
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        addRoleAdmin(authUser);
    }

    @Transactional
    void addRoleAdmin(CreateUserResult dbUserResult) {
        namedParameterJdbcTemplate.update("grant \"superadmin\" to \"" + dbUserResult.getUserId().toString() + "\"", Map.of());
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationMonsore() throws Exception {
        String appId;

        final CreateUserResult monsoereUser = authenticationService.createUser("monsore", "xxxxxxxx");
        UUID monsoreUserId = monsoereUser.getUserId();
        final OreSiUser publicUser = userRepository.findByLogin("_public_").orElse(null);
        final UUID publicUserId = publicUser.getId();
        Cookie monsoreCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "monsore")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        CreateUserResult withRightsUserResult = authenticationService.createUser("withrigths", "xxxxxxxx");
        String withRigthsUserId = withRightsUserResult.getUserId().toString();
        Cookie withRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        URL resource = getClass().getResource(fixtures.getMonsoreApplicationConfigurationResourceName());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            // on n'a pas le droit de creer de nouvelle application
            final NotApplicationCreatorRightsException resolvedException = (NotApplicationCreatorRightsException) mockMvc.perform(multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .cookie(monsoreCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResolvedException();
            addUserRightCreateApplication(monsoreUserId, "monsore");
            Assert.assertEquals("monsore", resolvedException.applicationName);
            addUserRightCreateApplication(monsoreUserId, "monsore");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .param("comment", "commentaire")
                            .cookie(monsoreCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            appId = JsonPath.parse(response).read("$.id");
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

        ApplicationResult applicationResult = objectMapper.readValue(response, ApplicationResult.class);

        Assert.assertEquals("commentaire", applicationResult.getComment());
        Assert.assertEquals("monsore", applicationResult.getName());
        Assert.assertEquals(Set.of("especes", "projet", "sites", "themes", "type de fichiers", "type_de_sites", "types_de_donnees_par_themes_de_sites_et_projet", "unites", "valeurs_qualitatives", "variables", "variables_et_unites_par_types_de_donnees"), applicationResult.getReferences().keySet());
//        Assert.assertEquals(List.of("pem"), applicationResult.getDataType());

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getMonsoreReferentielEspecestoTrimFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(monsoreCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        //test de la reference especetoTrim
        String getEspecesResponse = mockMvc.perform(get("/api/v1/applications/monsore/references/especes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(monsoreCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.referenceValues[*][?(@.hierarchicalKey=='lpf')].values.esp_definition_fr", IsNull.notNullValue()))
                //la colonne "esp_nom" et la colonne "esp_definition_fr"  ont bien été lues sans espaces
                .andExpect(jsonPath("$.referenceValues[*][?(@.hierarchicalKey=='lpf')].values.esp_definition_fr", Matchers.hasItem("LPF")))
                //les valeurs de la colonne "esp_definition_fr"  ont bien été lues sans espaces
                .andReturn().getResponse().getContentAsString();

        // Ajout de referentiel
        {
            for (Map.Entry<String, String> e : fixtures.getMonsoreReferentielFiles().entrySet()) {
                try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                    MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                    response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", e.getKey())
                                    .file(refFile)
                                    .cookie(monsoreCookie))
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

            String getReferencesResponse = mockMvc.perform(get("/api/v1/applications/monsore/references/sites")
                            .contentType(MediaType.APPLICATION_JSON)
                            .cookie(monsoreCookie))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsString();

            GetReferenceResult GetReferenceResult = objectMapper.readValue(getReferencesResponse, GetReferenceResult.class);
            Assert.assertEquals(9, GetReferenceResult.getReferenceValues().size());
        }

        // ajout de data
        resource = getClass().getResource(fixtures.getPemDataResourceName());
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            // sans droit on ne peut pas
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'monsore'"))
                    .andReturn().getResponse().getContentAsString();
            //ajout de droits withRigthsUserId
            final String jsonRightsForMonsoere = setJsonRightsForMonsoere(monsoreCookie, withRigthsUserId, OperationType.publication.name(), "pem");
            final String jsonRightsForMonsoereId = JsonPath.parse(jsonRightsForMonsoere).read("$.authorizationId");
            //avec les droits on peut publier
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));

            //sans droit on ne peut pas
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();

            //ajout de droits public
            final String publicRightsId = JsonPath.parse(setJsonRightsForMonsoere(monsoreCookie, "9032ffe5-bfc1-453d-814e-287cd678484a", OperationType.publication.name(), "pem")).read("$.authorizationId");


            //avec les droits public on peut publier même sans droit
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            // on supprime les droits public
            mockMvc.perform(delete("/api/v1/applications/monsore/authorization/" + publicRightsId)
                            .cookie(monsoreCookie))
                    .andExpect(status().is2xxSuccessful());

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }
        // ajout de data avec trim à faire
        resource = getClass().getResource(fixtures.getPemDataToTrimResourceName());
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            String actualJson = getPemData(monsoreCookie);
            log.debug(StringUtils.abbreviate(actualJson, 50));
        }
        mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(monsoreCookie))
                .andExpect(jsonPath("$.rows[*].values" +
                                "[?(@.projet.value=='projet_atlantique' )]" +
                                "[?(@.date.value=='date:1984-01-01T00:00:00:01/01/1984' )]" +
                                "[?(@.site.chemin=='plateforme.nivelle.nivelle__p1' )]" +
                                "[?(@.espece.value=='lpf' )]" +
                                "[?(@['Couleur des individus'].value=='couleur_des_individus__bleu' )]" +
                                "['Nombre d\\'individus'].value",
                        Matchers.hasItem("54")));

        //récupération du data

        try (InputStream pem = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String data = IOUtils.toString(Objects.requireNonNull(pem), StandardCharsets.UTF_8);
            String wrongData = data.replace("plateforme", "entete_inconnu");
            byte[] bytes = wrongData.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", bytes);
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(monsoreCookie))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }

        // list des types de data
        response = mockMvc.perform(get("/api/v1/applications/monsore/data")
                        .cookie(monsoreCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        log.debug(StringUtils.abbreviate(response, 50));

        {
            String expectedJson = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.json")), Charsets.UTF_8);
            JSONArray jsonArray = new JSONArray(expectedJson);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.getString(i));
            }

            String actualJson = getPemData(monsoreCookie);
            log.debug(StringUtils.abbreviate(actualJson, 50));
        }
        /**
         *  restitution de data json ajout de filtres et de tri
         * filtre :
         *  date.value between '01/01/1984' and '01/01/1984'
         *  Nombre d\\'individus'.value between 20 and 29 (==25)
         *  Couleur des individus.value == 'couleur_des_individus__vert'
         *
         *  tri :
         *      par site.plateforme -> a < p1 < p2
         *
         */
        {
            String filter = "{\"application\":null,\"applicationNameOrId\":null,\"dataType\":null,\"offset\":null,\"limit\":15,\"variableComponentSelects\":[],\"variableComponentFilters\":[{\"variableComponentKey\":{\"variable\":\"date\",\"component\":\"value\"},\"filter\":null,\"type\":\"date\",\"format\":\"dd/MM/yyyy\",\"intervalValues\":{\"from\":\"1984-01-01\",\"to\":\"1984-01-01\"}},{\"variableComponentKey\":{\"variable\":\"Nombre d'individus\",\"component\":\"value\"},\"filter\":null,\"type\":\"numeric\",\"format\":\"integer\",\"intervalValues\":{\"from\":\"20\",\"to\":\"29\"}},{\"variableComponentKey\":{\"variable\":\"Couleur des individus\",\"component\":\"value\"},\"filter\":\"vert\",\"type\":\"reference\",\"format\":\"uuid\",\"intervalValues\":null}],\"variableComponentOrderBy\":[{\"variableComponentKey\":{\"variable\":\"site\",\"component\":\"plateforme\"},\"order\":\"ASC\",\"type\":null,\"format\":null}]}";
            Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.json")), Charsets.UTF_8);
            String actualJson = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(monsoreCookie)
                            .param("downloadDatasetQuery", filter)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows", Matchers.hasSize(8)))
                    .andExpect(jsonPath("$.rows[*].values.date[?(@.value == 'date:1984-01-01T00:00:00:01/01/1984')]", Matchers.hasSize(8)))
                    .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'][?(@.value ==25)]", Matchers.hasSize(8)))
                    .andExpect(jsonPath("$.rows[*].values['Couleur des individus'][?(@.value =='couleur_des_individus__vert')]", Matchers.hasSize(8)))
                    .andExpect(jsonPath("$.rows[*].values.site.plateforme").value(Stream.of("a", "p1", "p1", "p1", "p1", "p1", "p2", "p2").collect(Collectors.toList())))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualJson, 50));

        }

        // restitution de data csv
        {
            String expectedCsv = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.csv")), Charsets.UTF_8);
            String actualCsv = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(monsoreCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    //     .andExpect(content().string(expectedCsv))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 50));
            /*List<String> actualCsvToList = Arrays.stream(actualCsv.split("\r?\n"))
                    .collect(Collectors.toList());
            List<String> expectedCsvToList = Arrays.stream(expectedCsv.split("\r?\n"))
                    .collect(Collectors.toList());
            Assert.assertEquals(expectedCsvToList.size(), actualCsvToList.size());
            actualCsvToList.forEach(expectedCsvToList::remove);
            Assert.assertTrue(expectedCsvToList.isEmpty());
            Assert.assertEquals(272, StringUtils.countMatches(actualCsv, "/1984"));*/
        }

        try (InputStream in = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String csv = IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
            String invalidCsv = csv
                    .replace("projet_manche", "projet_manch")
                    .replace("projet_atlantique", "projet_atlantiqu");
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", invalidCsv.getBytes(StandardCharsets.UTF_8));
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(monsoreCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
            Assert.assertTrue(response.contains("projet_manch"));
            Assert.assertTrue(response.contains("projet_atlantiqu"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("41"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("42"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("43"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("10"));
            Assert.assertFalse("L'erreur doit être tronquée", response.contains("141"));
            Assert.assertFalse("L'erreur doit être tronquée", response.contains("142"));
            Assert.assertFalse("L'erreur doit être tronquée", response.contains("143"));
        }
        final String getMonsoere = mockMvc.perform(get("/api/v1/applications/monsore")
                        .cookie(authCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        final String getSites = mockMvc.perform(get("/api/v1/applications/monsore/references/sites")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        final String getTypeSites = mockMvc.perform(get("/api/v1/applications/monsore/references/type_de_sites")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        final String getProjet = mockMvc.perform(get("/api/v1/applications/monsore/references/projet")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        final String getPem = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String adminRights = getJsonRightsforMonSoererepository(withRigthsUserId,
                OperationType.admin.name(),
                "pem",
                "plateforme.oir.oir__p1",
                "1984,1,1",
                "1984,1,5",
                monsoreCookie);

        final String getAuthorizations = mockMvc.perform(get("/api/v1/applications/monsore/authorization")
                        .cookie(withRigthsCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        final String getGrantable = mockMvc.perform(get("/api/v1/applications/monsore/grantable")
                        .cookie(withRigthsCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        registerFile("ui/cypress/fixtures/applications/ore/monsore/monsoere.json", getMonsoere);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/pem.json", getPem);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/references/type_de_sites.json", getTypeSites);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/references/sites.json", getSites);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/references/projet.json", getProjet);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/authorisation/grantable.json", getGrantable);
        registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/authorisation/authorizations.json", getAuthorizations);

//        // restitution de data json
//        resource = getClass().getResource("/data/compare/export.json");
//        try (InputStream in = resource.openStream()) {
//            String jsonCompare = new String(in.readAllBytes());
//            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir")
//                    .cookie(authCookie)
//                    .accept(MediaType.APPLICATION_JSON))
//                    .andExpect(status().isOk())
//                    .andExpect(content().json(jsonCompare))
//                    .andReturn().getResponse().getContentAsString();
//        }
//
//        // restitution de data csv
//        resource = getClass().getResource("/data/compare/export.csv");
//        try (InputStream in = resource.openStream()) {
//            String csvCompare = new String(in.readAllBytes());
//            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir")
//                    .cookie(authCookie)
//                    .accept(MediaType.TEXT_PLAIN))
//                    .andExpect(status().isOk())
//                    .andExpect(content().string(csvCompare))
//                    .andReturn().getResponse().getContentAsString();
//        }

//        // restitution de data csv
//        resource = getClass().getResource("/data/compare/exportColumn.csv");
//        try (InputStream in = resource.openStream()) {
//            String csvCompare = new String(in.readAllBytes());
//            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir&outColumn=date;espece;plateforme;Nombre d'individus")
//                    .cookie(authCookie)
//                    .accept(MediaType.TEXT_PLAIN))
//                    .andExpect(status().isOk())
//                    .andExpect(content().string(csvCompare))
//                    .andReturn().getResponse().getContentAsString();
//        }

//        // recuperation de l'id du referentiel
//        response = mockMvc.perform(get("/api/v1/applications/monsore/references/especes?esp_nom=LPF")
//                .contentType(MediaType.APPLICATION_JSON)
//                .cookie(authCookie))
//                .andExpect(status().isOk())
//                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
//                .andReturn().getResponse().getContentAsString();
//
//        ReferenceValue[] refEspeces = objectMapper.readValue(response, ReferenceValue[].class);
//        UUID refId = Stream.of(refEspeces).map(ReferenceValue::getId).findFirst().orElseThrow();
//
//        // creation d'un user qui aura le droit de lire les données mais pas certain referenciel
//        OreSiUser restrictedReader = authRepository.createUser("UnPetitReader", "xxxxxxxx");
//        mockMvc.perform(put("/api/v1/applications/{nameOrId}/users/{role}/{userId}",
//                appId, ApplicationRight.RESTRICTED_READER.name(), restrictedReader.getId().toString())
//                .contentType(MediaType.APPLICATION_JSON)
//                .cookie(authCookie)
//                .content("[\"" + refId + "\"]"))
//                .andExpect(status().isOk());

//        Cookie authRestrictedReaderCookie = mockMvc.perform(post("/api/v1/login")
//                .param("login", "UnPetitReader")
//                .param("password", "xxxxxxxx"))
//                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

//        // restitution de data csv
//        resource = getClass().getResource("/data/compare/exportColumnRestrictedReader.csv");
//        try (InputStream in = resource.openStream()) {
//            String csvCompare = new String(in.readAllBytes());
//            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir&outColumn=date;espece;plateforme;Nombre d'individus")
//                    .cookie(authRestrictedReaderCookie)
//                    .accept(MediaType.TEXT_PLAIN))
//                    .andExpect(status().isOk())
//                    .andExpect(content().string(csvCompare))
//                    .andReturn().getResponse().getContentAsString();
//        }

        // changement du fichier de config avec un mauvais (qui ne permet pas d'importer les fichiers
    }

    private String getPemData(Cookie monsoreCookie) throws Exception {
        return mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(monsoreCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables").isArray())
                .andExpect(jsonPath("$.variables", Matchers.hasSize(6)))
                .andExpect(jsonPath("$.variables").value(Stream.of("date", "projet", "site", "Couleur des individus", "Nombre d'individus", "espece").collect(Collectors.toList())))
                .andExpect(jsonPath("$.checkedFormatVariableComponents.DateLineChecker", IsNull.notNullValue()))
                .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker", IsNull.notNullValue()))
                .andExpect(jsonPath("$.checkedFormatVariableComponents.IntegerChecker", IsNull.notNullValue()))
                .andExpect(jsonPath("$.rows").isArray())
                .andExpect(jsonPath("$.rows", Matchers.hasSize(272)))
                //.andExpect(jsonPath("$.rows.value").value(list))
                .andExpect(jsonPath("$.totalRows", Is.is(272)))
                .andExpect(jsonPath("$.rows[*].values.date.value", Matchers.hasSize(272)))
                .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'].unit", Matchers.hasSize(272)))
                .andExpect(jsonPath("$.rows[*].values['Couleur des individus'].unit", Matchers.hasSize(272)))
                .andReturn().getResponse().getContentAsString();
    }

    public void registerFile(String filePath, String jsonContent) throws IOException {
        jsonContent = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonContent);
        File errorsFile = new File(filePath);
        log.debug(errorsFile.getAbsolutePath());
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(jsonContent);
        writer.close();
    }


    @Test
    @Category(OTHERS_TEST.class)
    public void testMultiplicityMany() throws Exception {
        URL resource = getClass().getResource(fixtures.getMultiplicityMany());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "multiplicity");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/multiplicity")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/multiplicity", "ALL,REFERENCETYPE")
                            .cookie(authCookie)
                            .param("filter", "ALL"))
                    .andExpect(status().is2xxSuccessful());
        }
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getMultiplicityReferencesFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                String response = mockMvc.perform(multipart("/api/v1/applications/multiplicity/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/multiplicity/references/reference1")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues[*].values.[?(@.projets==[\"4\",\"5\",\"9\"])]", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.referenceValues[*].values.[?(@.names==[\"toto1.3\",\"toto1.1\",\"toto1.2\"])]", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.referenceValues[*].values.[?(@.duration==[\"3.2\",\"4.5\",\"5.6\"])]", Matchers.hasSize(3)))
                .andExpect(jsonPath("$.referenceValues[*].values.dates[0]", Matchers.hasItems("date:2014-01-20T00:00:00:23/06/2014")));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/multiplicity/references/reference2")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues[*].values.reference1[0]", Matchers.hasItems("toto__toto1", "tutu__tutu2")));
        try (InputStream refStream = getClass().getResourceAsStream(fixtures.getMultiplicityManyData())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "bugs.csv", "text/plain", refStream);

            String response = mockMvc.perform(multipart("/api/v1/applications/multiplicity/data/bugs")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            response = mockMvc.perform(get("/api/v1/applications/multiplicity/data/bugs")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker.bug_reference1.referenceLineChecker.referenceValues", Matchers.hasKey("tutu__tutu1")))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker.bug_reference1.referenceLineChecker.referenceValues", Matchers.hasKey("tutu__tutu2")))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker.bug_reference1.referenceLineChecker.referenceValues", Matchers.hasKey("toto__toto1")))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker.bug_reference1.referenceLineChecker.referenceValues", Matchers.hasKey("toto__toto2")))
                    .andExpect(jsonPath("$.rows[0].values.bug.dates", Matchers.is("[date:2002-01-23T00:00:00:23/01/2002,24/01/2002,date:2002-01-24T00:00:00:23/01/2002,24/01/2002]")))
                    .andExpect(jsonPath("$.rows[0].values.bug.projets", Matchers.is("1,2")))
                    .andExpect(jsonPath("$.rows[0].values.bug.fichiers", Matchers.is("file1,file2")))
                    .andExpect(jsonPath("$.rows[0].values.bug.durations", Matchers.is("3.2,5.4")))
                    .andExpect(jsonPath("$.rows[0].values.bug.reference1", Matchers.is("toto__toto1,tutu__tutu1")))
                    .andReturn().getResponse().getContentAsString();
        }
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationWithComputedComponentsWithReferences() throws Exception {
        URL resource = getClass().getResource(fixtures.getApplicationWithComputedComponentsWithReferences());

        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "minautor");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/minautor")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();
        }
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getApplicationWithComputedComponentsWithReferencesReferences().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/minautor/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        // Ajout de data
        for (Map.Entry<String, String> e : fixtures.getApplicationWithComputedComponentsWithReferencesData().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                mockMvc.perform(multipart("/api/v1/applications/minautor/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        mockMvc.perform(get("/api/v1/applications/minautor/data/dataset")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values.informations.site", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.site", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.rows[*].values.informations.parcelle", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.parcelle", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.rows[*].values.informations.bloc", Matchers.hasSize(4)))
                .andExpect(jsonPath("$.rows[*].refsLinkedTo.informations.bloc", Matchers.hasSize(4)));
    }


    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationMonsoreWithRepository() throws Exception {
        URL resource = getClass().getResource(fixtures.getMonsoreApplicationConfigurationWithRepositoryResourceName());
        String oirFilesUUID;
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "monsore");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/monsore", "ALL,REFERENCETYPE")
                            .cookie(authCookie)
                            .param("filter", "ALL"))
                    .andExpect(status().is2xxSuccessful())
                    ///vérification de la sauvegarde des tags
                    .andExpect(jsonPath("$.references.type_de_sites.tags", Matchers.hasItem("context")))
                    .andExpect(jsonPath("$.references.sites.tags", Matchers.hasItem("context")))
                    .andExpect(jsonPath("$.references.projet.tags", Matchers.hasItem("context")))
                    .andExpect(jsonPath("$.references.types_de_donnees_par_themes_de_sites_et_projet.tags", Matchers.hasItem("context")))
                    .andExpect(jsonPath("$.references.especes.tags", Matchers.hasItem("data")))
                    .andExpect(jsonPath("$.references['type de fichiers'].tags", Matchers.hasItem("no-tag")))
                    .andExpect(jsonPath("$.references.variables.tags", Matchers.hasItem("data")))
                    .andExpect(jsonPath("$.references.unites.tags", Matchers.hasItem("data")))
                    .andExpect(jsonPath("$.references.valeurs_qualitatives.tags", Matchers.hasItem("data")))
                    .andExpect(jsonPath("$.references.variables_et_unites_par_types_de_donnees.tags", Matchers.hasItem("data")))
                    .andExpect(jsonPath("$.internationalization.internationalizedTags.context.fr", Is.is("contexte")))
                    .andExpect(jsonPath("$.rightsRequest.description.format.endDate", Matchers.not(Matchers.empty())))
                    .andExpect(jsonPath("$.configuration.rightsRequest.format.organization", Matchers.not(Matchers.empty())));
        }
        CreateUserResult withRightsUserResult = authenticationService.createUser("withrigths", "xxxxxxxx");
        String withRigthsUserId = withRightsUserResult.getUserId().toString();
        Cookie withRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        final String typeDeSites = fixtures.getMonsoreReferentielFiles().get("type_de_sites");
        /**
         * on test la demande de droits pour un utilisateur lambda
         */
        {
            String rightsRequest = "{\n" +
                    "  \"id\": \"\",\n" +
                    "  \"comment\": \"Un commentaire\",\n" +
                    "  \"fields\": {\n" +
                    "    \"organization\": \"INRAE\",\n" +
                    "    \"project\": \"openAdom\",\n" +
                    "    \"startDate\": \"10/10/1010\",\n" +
                    "    \"startDate\": \"10/11/1010\",\n" +
                    "    \"projectManagers\": \"toto,titi\"\n" +
                    "  },\n" +
                    "  \"rightsRequest\": {\n" +
                    "   \"usersId\": null,\n" +
                    "   \"applicationNameOrId\":\"monsore\",\n" +
                    "   \"id\": null,\n" +
                    "   \"name\": \"une authorization sur monsore\",\n" +
                    "   \"dataType\":\"pem\",\n" +
                    "   \"authorizations\":{\n" +
                    "   \"pem\":{\n" +
                    "        \"extraction\":[\n" +
                    "              {\n" +
                    "              \"requiredAuthorizations\":{\n" +
                    "                 \"projet\":\"projet_manche\",\n" +
                    "                  \"localization\":\"plateforme.nivelle.nivelle__p1\"\n" +
                    "              },\n" +
                    "                 \"dataGroups\":[\n" +
                    "                    \"all\"\n" +
                    "                 ],\n" +
                    "                \"intervalDates\":{\n" +
                    "                   \"fromDay\":[1984,1,1],\n" +
                    "                   \"toDay\":[1984,1,6]\n" +
                    "                }\n" +
                    "             }\n" +
                    "         ]\n" +
                    "   }" +
                    "}\n" +
                    "}\n" +
                    "}";

            String response = mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rightsRequest)
                            .cookie(authCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            response = mockMvc.perform((multipart("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rightsRequest)
                            .cookie(lambdaCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            String json = "{\n" +
                    "  \"uuids\": [],\n" +
                    "  \"authorizations\": [],\n" +
                    "  \"locale\": \"fr_FR\",\n" +
                    "  \"offset\": 0,\n" +
                    "  \"limit\": 1,\n" +
                    "  \"fieldFilters\": [\n" +
                    "    {\n" +
                    "      \"field\": \"organization\",\n" +
                    "      \"filter\": \"INRAE\",\n" +
                    "      \"type\": null,\n" +
                    "      \"format\": null,\n" +
                    "      \"intervalValues\": null,\n" +
                    "      \"isRegExp\": null\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            response = mockMvc.perform((get("/api/v1/applications/monsore/rightsRequest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .param("params", json)
                            .cookie(lambdaCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        String response = null;
        try (InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
            MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", "type_de_sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'monsore'"))
                    .andReturn().getResponse().getContentAsString();
        }

        String referencesRight = getJsonReferenceRightsforMonSoererepository(withRigthsUserId, "manage");
        referencesRight = JsonPath.parse(referencesRight).read("authorizationId");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userId", withRigthsUserId);
        params.add("offset", "0");
        params.add("limit", "1");
        response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/monsore/references/authorization")
                        .params(params)
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.authorizationResults[*].users[*].login", Matchers.hasItem("withrigths")))
                .andExpect(jsonPath("$.authorizationResults[*].users[*].id", Matchers.hasItem(withRightsUserResult.getUserId().toString())))
                .andExpect(jsonPath("$.authorizationResults[*].authorizations.manage", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.authorizationsForUser.applicationName", Is.is("monsore")))
                .andExpect(jsonPath("$.authorizationsForUser.authorizationResults.manage", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.authorizationsForUser.authorizationResults.manage", Matchers.hasItem("sites")))
                .andExpect(jsonPath("$.authorizationsForUser.authorizationResults.manage", Matchers.hasItem("type_de_sites")))
                .andReturn()
                .getResponse().getContentAsString();
        response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/monsore/references/authorization")
                        .params(params)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.authorizationResults[*].users[*].login", Matchers.hasItem("withrigths")))
                .andExpect(jsonPath("$.authorizationResults[*].users[*].id", Matchers.hasItem(withRightsUserResult.getUserId().toString())))
                .andExpect(jsonPath("$.authorizationResults[*].authorizations.manage[*]", Matchers.hasItem("type_de_sites")))
                .andExpect(jsonPath("$.authorizationResults[*].authorizations.manage[*]", Matchers.hasItem("sites")))
                .andExpect(jsonPath("$.authorizationsForUser.applicationName", Is.is("monsore")))
                .andExpect(jsonPath("$.authorizationsForUser.authorizationResults[*]", Matchers.hasSize(0)))
                .andReturn()
                .getResponse().getContentAsString();
        final String read = JsonPath.parse(response).read("$.authorizationResults[0].uuid");


        response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/monsore/references/authorization/{userLoginOrId}", "withrigths")
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.authorizationResults.manage[*]", Matchers.hasItem("type_de_sites")))
                .andExpect(jsonPath("$.authorizationResults.manage[*]", Matchers.hasItem("sites")))
                .andExpect(jsonPath("$.applicationName", Is.is("monsore")))
                .andReturn()
                .getResponse().getContentAsString();

        try (InputStream refStream = getClass().getResourceAsStream(typeDeSites)) {
            MockMultipartFile refFile = new MockMultipartFile("file", typeDeSites, "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", "type_de_sites")
                            .file(refFile)
                            .cookie(withRigthsCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getMonsoreReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        String additionalJsonRequest = "{\n" +
                "  \"uuids\": [],\n" +
                "  \"additionalFilesInfos\": {\n" +
                "    \"fichiers\": {\n" +
                "      \"fieldFilters\": [\n" +
                "        {\n" +
                "          \"field\": \"nom\",\n" +
                "          \"filter\": \"dix\",\n" +
                "          \"type\": \"\",\n" +
                "          \"format\": null,\n" +
                "          \"intervalValues\": null,\n" +
                "          \"isRegExp\": false\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"locale\": \"fr_FR\",\n" +
                "  \"offset\": 0,\n" +
                "  \"limit\": null\n" +
                "}";
        String additionalfileUUID;
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile addFile = new MockMultipartFile("file", "monsoere.yaml", "text/plain", in);
            String json = "{\n" +
                    "  \"id\": \"\",\n" +
                    "  \"fileType\": \"fichiers\",\n" +
                    "  \"fields\": {\n" +
                    "    \"age\": \"10\",\n" +
                    "    \"nom\": \"dix\",\n" +
                    "    \"date\": \"10/10/1010\",\n" +
                    "    \"site\": \"oir\",\n" +
                    "    \"poids\": \"10.10\"\n" +
                    "  },\n" +
                    "  \"associates\": {\n" +
                    "    \"authorizations\": {\n" +
                    "      \"pem\": {\n" +
                    "        \"associate\": [\n" +
                    "          {\n" +
                    "            \"dataGroups\": [],\n" +
                    "            \"requiredAuthorizations\": {\n" +
                    "              \"projet\":  \"projet_atlantique\"\n" +
                    "            }\n" +
                    "          },\n" +
                    "          {\n" +
                    "            \"dataGroups\": [],\n" +
                    "            \"requiredAuthorizations\": {\n" +
                    "              \"projet\": \"projet_manche\"\n" +
                    "            }\n" +
                    "          }\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";
            additionalfileUUID = mockMvc.perform((multipart("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .file(addFile)
                            .param("params", json)
                            .cookie(authCookie)))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                    .cookie(authCookie));

            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful());

            String error = mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles/fichiers")
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResolvedException().getMessage();
            Assert.assertEquals("application inconnue 'monsore'", error);
            //pas de droits
            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                            .param("nameOrId", "monsore")
                            .param("params", additionalJsonRequest)
                            .cookie(lambdaCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(result -> Assert.assertEquals("application inconnue 'monsore'", result.getResolvedException().getMessage()));


            Assert.assertEquals("application inconnue 'monsore'", error);
            //avec droits
            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                            .param("nameOrId", "monsore")
                            .param("params", additionalJsonRequest)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(result -> {
                        List<ZipEntry> entries = new ArrayList<>();
                        ZipInputStream zi = null;
                        try {
                            zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));

                            ZipEntry zipEntry = null;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        } finally {
                            if (zi != null) {
                                zi.close();
                            }
                        }
                        final List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .collect(Collectors.toList());
                        Assert.assertTrue(entryNames.contains("fichiers/monsoere/monsoere_infos.txt"));
                        Assert.assertTrue(entryNames.contains("fichiers/monsoere/monsoere.yaml"));
                    });
            ;
            mockMvc.perform(get("/api/v1/applications/monsore/additionalFiles")
                            .param("nameOrId", "monsore")
                            .param("params", "{\"uuids\":null,\"fileNames\":null,\"additionalFilesInfos\":{\"fichiers\":{\"fieldFilters\":[]}}}")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful()).andExpect(result -> {
                        List<ZipEntry> entries = new ArrayList<>();
                        ZipInputStream zi = null;
                        try {
                            zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));

                            ZipEntry zipEntry = null;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        } finally {
                            if (zi != null) {
                                zi.close();
                            }
                        }
                        final List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .collect(Collectors.toList());
                        Assert.assertTrue(entryNames.contains("fichiers/monsoere/monsoere_infos.txt"));
                        Assert.assertTrue(entryNames.contains("fichiers/monsoere/monsoere.yaml"));
                    });
            ;

        }

        // ajout de data
        String projet = "manche";
        String plateforme = "plateforme";
        String site = "oir";
        resource = getClass().getResource(fixtures.getPemRepositoryDataResourceName(projet, site));


        // on dépose 3 fois le même fichier sans le publier
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);


            // sans droit dépôt impossible de déposer
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .param("params", fixtures.getPemRepositoryParams(projet, plateforme, site, false))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.message", Is.is("noRightsForDeposit")))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);

            String createRights = getJsonRightsforMonSoererepository(withRigthsUserId, OperationType.depot.name(), "pem", "plateforme.oir.oir__p1", "1984,1,1", "1984,1,5", authCookie);

            //fileOrUUID.binaryFileDataset/applications/{name}/file/{id}
            for (int i = 0; i < 3; i++) {
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                                .file(refFile)
                                .param("params", fixtures.getPemRepositoryParams(projet, plateforme, site, false))
                                .cookie(withRigthsCookie))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn().getResponse().getContentAsString();
                log.debug(response);
            }
            //on regarde les versions déposées
            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", Matchers.hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == false )]", Matchers.hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )]", Matchers.hasSize(0)))
                    .andReturn().getResponse().getContentAsString();
//            log.debug(response);
            //récupération de l'identifiant de la dernière version déposée
            oirFilesUUID = JsonPath.parse(response).read("$[2].id");

            // on vérifie l'absence de data
            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows").value(-1))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);

            // on publie le dernier fichier déposé sans les droits

            final Exception exception = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .param("params", fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, true))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResolvedException();

            Assert.assertTrue(exception instanceof SiOreIllegalArgumentException);
            Assert.assertEquals("noRightForPublish", exception.getMessage());
            Assert.assertEquals("pem", ((SiOreIllegalArgumentException) exception).getParams().get("dataType"));
            Assert.assertEquals("monsore", ((SiOreIllegalArgumentException) exception).getParams().get("application"));


            // on donne les droits publication


            createRights = getJsonRightsforMonSoererepository(withRigthsUserId, OperationType.publication.name(), "pem", "plateforme.oir.oir__p1", "1984,1,1", "1984,1,6", authCookie);


            // on publie le dernier fichier déposé

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .param("params", fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, true))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            // on récupère la liste des versions déposées

            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(withRigthsCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", Matchers.hasSize(3)))
                    .andExpect(jsonPath("$[*][?(@.params.published == false )]", Matchers.hasSize(2)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )]", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(oirFilesUUID))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            // on récupère le data en base

            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows").value(34))
                    .andExpect(jsonPath("$.rows[*]", Matchers.hasSize(34)))
                    .andExpect(jsonPath("$.rows[*].values[? (@.site.chemin == 'plateforme.oir.oir__p1')][? (@.projet.value == 'projet_manche')]", Matchers.hasSize(34)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            byte[] responseToByteArray = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .accept(MediaType.TEXT_PLAIN_VALUE)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(status().is2xxSuccessful()).andExpect(result -> {
                        List<ZipEntry> entries = new ArrayList<>();
                        ZipInputStream zi = null;
                        try {
                            zi = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));

                            ZipEntry zipEntry = null;
                            while ((zipEntry = zi.getNextEntry()) != null) {
                                entries.add(zipEntry);
                            }
                        } finally {
                            if (zi != null) {
                                zi.close();
                            }
                        }
                        final List<String> entryNames = entries.stream()
                                .map(ZipEntry::getName)
                                .collect(Collectors.toList());
                        Assert.assertTrue(entryNames.contains("pem.csv"));
                        Assert.assertTrue(entryNames.contains("references/variables_et_unites_par_types_de_donnees.csv"));
                        Assert.assertTrue(entryNames.contains("references/sites.csv"));
                        Assert.assertTrue(entryNames.contains("references/types_de_donnees_par_themes_de_sites_et_projet.csv"));
                        Assert.assertTrue(entryNames.contains("additionalFiles/fichiers/monsoere/monsoere_infos.txt"));
                        Assert.assertTrue(entryNames.contains("additionalFiles/fichiers/monsoere/monsoere.yaml"));
                    })
                    .andReturn().getResponse().getContentAsByteArray();

            Files.write(Path.of("/tmp/data.zip"), responseToByteArray);
        }
        //on publie 4 fichiers

        publishOrDepublish(authCookie, "manche", "plateforme", "scarff", 68, true, 1, true);
        publishOrDepublish(authCookie, "atlantique", "plateforme", "scarff", 34, true, 1, true);
        publishOrDepublish(authCookie, "atlantique", "plateforme", "nivelle", 34, true, 1, true);
        publishOrDepublish(authCookie, "manche", "plateforme", "nivelle", 34, true, 1, true);
        //on publie une autre version
        String fileUUID = publishOrDepublish(authCookie, "manche", "plateforme", "nivelle", 34, true, 2, true);
        // on supprime l'application publiée
        response = mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/applications/monsore/file/" + fileUUID)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));
        try {
            publishOrDepublish(withRigthsCookie, "manche", "plateforme", "nivelle", 34, true, 1, true);

        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("noRightOnTable", e.getMessage());
            Assert.assertEquals("data", e.getParams().get("table"));
        }
        final String createRights = getJsonRightsforMonSoererepository(withRigthsUserId, OperationType.publication.name(), "pem", "plateforme.nivelle.nivelle__p1", "1984,1,1", "1984,1,6", authCookie);

        //les droit s de publication permettent aussi le dépôt
        final String fileUUID2 = publishOrDepublish(withRigthsCookie, "manche", "plateforme", "nivelle", 34, true, 2, true);

        testFilesAndDataOnServer(plateforme, "manche", "nivelle", 0, 2, fileUUID2, true);


        // on depublie le fichier oir déposé (les droits publication valent dépublication

        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                        .param("params", fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, false))
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère la liste des versions déposées

        response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                        .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == false )]", Matchers.hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == true )]", Matchers.hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère le data en base si j'ai les droits de publication je peux aussi lire les données avec ces droits

        response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.nivelle.nivelle__p1\")].chemin", Matchers.hasSize(34)))
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.oir.oir__p1\")].chemin", Matchers.hasSize(34)))
                .andExpect(jsonPath("$.totalRows").value(68))
                .andExpect(jsonPath("$.rows[*]", Matchers.hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values[? (@.site.chemin == 'oir__p1')][? (@.projet.value == 'projet_manche')]", Matchers.hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.scarff.scarff__p1\")].chemin", Matchers.hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.scarff.scarff__p1\")].chemin", Matchers.hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.nivelle.nivelle__p1\")].chemin", Matchers.hasSize(68)))
                .andExpect(jsonPath("$.rows[*].values.site[?(@.chemin==\"plateforme.oir.oir__p1\")].chemin", Matchers.hasSize(34)))
                .andExpect(jsonPath("$.totalRows").value(170))
                .andExpect(jsonPath("$.rows[*]", Matchers.hasSize(170)))
                .andExpect(jsonPath("$.rows[*].values[? (@.site.chemin == 'oir__p1')][? (@.projet.value == 'projet_manche')]", Matchers.hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));
        // on supprime le fichier on peut dépublier mais pas supprimer le fichier
        final NotApplicationCanDeleteRightsException resolvedException = (NotApplicationCanDeleteRightsException) mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID2)
                        .cookie(withRigthsCookie))
                .andExpect(status().is4xxClientError())
                .andReturn()
                .getResolvedException();
        Assert.assertEquals("NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION", resolvedException.getMessage());
        Assert.assertEquals("pem", resolvedException.getDataType());
        Assert.assertEquals("monsore", resolvedException.getApplicationName());

        //on donne les droits de suppression
        final String deleteRights = getJsonRightsforMonSoererepository(withRigthsUserId, OperationType.delete.name(), "pem", "plateforme.nivelle.nivelle__p1", "1984,1,1", "1984,1,6", authCookie);

        // on supprime le fichier a les droits car à les droits de publication
        mockMvc.perform(delete("/api/v1/applications/monsore/file/" + fileUUID2)
                        .cookie(withRigthsCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string(fileUUID2));
        // on supprime le fichier additionnel

        mockMvc.perform(delete("/api/v1/applications/monsore/additionalFiles")
                        .param("nameOrId", "monsore")
                        .param("params", additionalJsonRequest)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(result -> Assert.assertEquals(additionalfileUUID.replace("\"", ""), result.getResponse().getContentAsString()));
    }

    private String getJsonRightsforMonSoererepository(String withRigthsUserId, String role, String datatype, String localization, String from, String to, Cookie authenticateCookie) throws Exception {
        authenticateCookie = authenticateCookie == null ? authCookie : authenticateCookie;
        final String json = String.format("{\n" +
                "   \"usersId\":[\"" + withRigthsUserId + "\"],\n" +
                "   \"applicationNameOrId\":\"monsore\",\n" +
                "   \"id\": null,\n" +
                "   \"name\": \"une authorization sur monsore\",\n" +
                "   \"dataType\":\"pem\",\n" +
                "   \"authorizations\":{\n" +
                "      \"%5$s\": {\n" +
                "        \"%1$s\":[\n" +
                "            {\n" +
                "               \"requiredAuthorizations\":{\n" +
                "                 \"projet\":\"projet_manche\",\n" +
                "                  \"localization\":\"%2$s\"\n" +
                "                 },\n" +
                "                \"datagroups\":[\n" +
                "                   \"all\"\n" +
                "                ],\n" +
                "                \"intervalDates\":{\n" +
                "                   \"fromDay\":[%3$s],\n" +
                "                   \"toDay\":[%4$s]\n" +
                "                }\n" +
                "         }\n" +
                "       ]\n" +
                "      }\n" +
                "}\n" +
                "}", role, localization, from, to, datatype);
        final MockHttpServletRequestBuilder createRight = post("/api/v1/applications/monsore/authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authenticateCookie)
                .content(json);
        return mockMvc.perform(createRight)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String getJsonReferenceRightsforMonSoererepository(String withRigthsUserId, String role) throws Exception {
        final String json = String.format("{\n" +
                "   \"usersId\":[\"" + withRigthsUserId + "\"],\n" +
                "   \"applicationNameOrId\":\"monsore\",\n" +
                "   \"id\": null,\n" +
                "   \"name\": \"une authorization sur le référentiel monsore\",\n" +
                "   \"dataType\":\"pem\",\n" +
                "   \"references\":{\n" +
                "   \"%1$s\":[\"type_de_sites\",\"sites\"]\n" +
                "}\n" +
                "}", role);
        final MockHttpServletRequestBuilder createRight = post("/api/v1/applications/monsore/references/authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie)
                .content(json);
        return mockMvc.perform(createRight)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }


    private String setJsonRightsForMonsoere(Cookie cookie, String withRigthsUserId, String role, String datatype) throws Exception {
        final String json = String.format("{\n" +
                "   \"usersId\":[\"" + withRigthsUserId + "\"],\n" +
                "   \"applicationNameOrId\":\"monsore\",\n" +
                "   \"id\": null,\n" +
                "   \"name\": \"une authorization sur monsore\",\n" +
                "   \"dataType\":\"pem\",\n" +
                "   \"authorizations\":{\n" +
                "      \"%2$s\":{\n" +
                "        \"%1$s\":[\n" +
                "               {" +
                "                \"dataGroups\": [],\n" +
                "               \"requiredAuthorizations\": {\n" +
                "                  \"projet\": \"projet_atlantique\"\n" +
                "                },\n" +
                "              \"fromDay\": null,\n" +
                "               \"toDay\": null\n" +
                "             },\n" +
                "            {\n" +
                "             \"dataGroups\": [],\n" +
                "              \"requiredAuthorizations\": {\n" +
                "                \"projet\": \"projet_manche\"\n" +
                "                },\n" +
                "               \"fromDay\": null,\n" +
                "               \"toDay\": null\n" +
                "             }\n" +
                "        ]\n" +
                "      }\n" +
                "   }\n" +
                "}", role, datatype);
        final MockHttpServletRequestBuilder createRight = post("/api/v1/applications/monsore//authorization")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookie)
                .content(json);
        return mockMvc.perform(createRight)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String publishOrDepublish(Cookie cookie, String projet, String plateforme, String site, int expected, boolean toPublish, int numberOfVersions, boolean published) throws Exception {
        URL resource;
        String response;
        resource = getClass().getResource(fixtures.getPemRepositoryDataResourceName(projet, site));
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {

            //dépôt et publication d'un fichier projet site__p1
            MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
            refFile.transferTo(Path.of("/tmp/pem.csv"));
            final MvcResult mockResponse = mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .param("params", fixtures.getPemRepositoryParams(projet, plateforme, site, toPublish))
                            .cookie(cookie))
                    .andReturn();
            if (mockResponse.getResponse().getStatus() >= 200 && mockResponse.getResponse().getStatus() < 300) {
                String fileUUID = JsonPath.parse(mockResponse.getResponse().getContentAsString()).read("$.fileId");
                testFilesAndDataOnServer(plateforme, projet, site, expected, numberOfVersions, fileUUID, published);
                log.debug(StringUtils.abbreviate(mockResponse.getResponse().getContentAsString(), 50));
                return fileUUID;
            }
            throw mockResponse.getResolvedException();
        }
    }

    /**
     * This is a case where a datatype has no authorization section.
     * The only authorizations that can be put on are on none or all values.
     *
     * @throws Exception
     */
    @Test
    public void testProgressiveYamlWithoutAuthorization() throws Exception {
        String authorizationId;
        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("yamlWithoutAuthorization"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }
        //pas de referentiels
        progressiveYamlAddData();

        String lambdaUserId = lambdaUser.getUserId().toString();
        Cookie readerCookies = mockMvc.perform(post("/api/v1/login")
                        .param("login", "lambda")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);


        {
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(readerCookies)
                    )
                    .andExpect(jsonPath("$[0].name", IsEqual.equalTo("progressive")))
                    .andReturn().getResponse().getContentAsString();
        }

        {
            mockMvc.perform(get("/api/v1/applications/progressive/data/date_de_visite")
                            .cookie(readerCookies)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
           /* final Authorization authorization = new Authorization(List.of(), Map.of(), new LocalDateTimeRange());
            final CreateAuthorizationRequest createAuthorizationRequest = new CreateAuthorizationRequest(
                    null,
                    "progressiveName",
                    Set.of(lambdaUserId),
                    "progressive",
                    "date_de_visite",
                    Map.of(OperationType.extraction,List.of(authorization))
            );
             String json =new ObjectMapper().writeValueAsString(createAuthorizationRequest);
*/
            String json = String.format("{\n" +
                    "   \"usersId\":[\"%1$s\"],\n" +
                    "   \"applicationNameOrId\":\"progressive\",\n" +
                    "   \"id\": null,\n" +
                    "   \"name\": \"une authorization sur progressive\",\n" +
                    "   \"authorizations\":{\n" +
                    "   \"%2$s\":{\n" +
                    "   \"extraction\":[\n" +
                    "      {\n" +
                    "         \"requiredAuthorizations\":{},\n" +
                    "         \"dataGroup\":[],\n" +
                    "         \"intervalDates\":{\n" +
                    "            \"fromDay\":null,\n" +
                    "            \"toDay\":null\n" +
                    "         }\n" +
                    "      }\n" +
                    "   ]\n" +
                    "}\n" +
                    "}\n" +
                    "}", lambdaUserId, "date_de_visite");

            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(readerCookies)
                    )
                    .andExpect(jsonPath("$[0].name", IsEqual.equalTo("progressive")))
                    .andReturn().getResponse().getContentAsString();


            MockHttpServletRequestBuilder create = post("/api/v1/applications/progressive/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            authorizationId = JsonPath.parse(response).read("$.authorizationId", String.class);
            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            // Une fois l'accès donné, on doit pouvoir avec l'application dans la liste"
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(readerCookies)
                    )
                    .andExpect(jsonPath("$[*].name", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[*].name", Matchers.contains("progressive")))
                    .andReturn().getResponse().getContentAsString();
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/progressive/data/date_de_visite")
                            .cookie(readerCookies)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.relevant.numero").value(hasItemInArray(equalTo("125")), String[].class))
                    .andReturn().getResponse().getContentAsString();
        }
        MockHttpServletRequestBuilder delete = delete(String.format("/api/v1/applications/progressive/authorization/%s", authorizationId))
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie);
        mockMvc.perform(delete)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        // L'utilisateur sans droit ne peut voir les applications

        //TODO

            /*String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(readerCookies)
                    )
                    .andExpect(jsonPath("$[*].name", Matchers.hasSize(0)))
                    .andReturn().getResponse().getContentAsString();*/


    }

    @Test
    public void testProgressiveYamlWithEmptyDatagroup() throws Exception {

        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("yamlWithEmptyDatagroup"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();
        }

        progressiveYamlAddReferences();
        progressiveYamlAddData();
    }

    /**
     * Test that a localisationScope referes to a variable component with checker references
     *
     * @throws Exception
     */
    @Test
    public void testProgressiveYamlWithNoReference() throws Exception {

        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("testAuthorizationScopeWithoutReference"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            BadApplicationConfigurationException exception = (BadApplicationConfigurationException) mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    //.andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResolvedException();
            final ValidationCheckResult validationCheckResult = exception.getConfigurationParsingResult().getValidationCheckResults()
                    .get(0);
            Assert.assertEquals("authorizationScopeMissingReferenceCheckerForAuthorizationScope", validationCheckResult.getMessage());
            final Map<String, Object> messageParams = validationCheckResult.getMessageParams();
            Assert.assertEquals("localization", messageParams.get("authorizationScopeName"));
            Assert.assertEquals("date_de_visite", messageParams.get("dataType"));
            Assert.assertEquals("agroecosysteme", messageParams.get("component"));
            Assert.assertEquals("localisation", messageParams.get("variable"));
        }
    }

    @Test
    public void testProgressiveYamlWithoutAuthorizationScope() throws Exception {

        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("testProgressiveYamlWithoutAuthorizationScope"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());

            //pas de référentiel
            progressiveYamlAddData();
        }
    }

    @Test
    public void testProgressiveYamlWithoutTimescopeScope() throws Exception {

        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("testProgressiveYamlWithoutTimescopeScope"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            final ResultActions resultActions = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());

            progressiveYamlAddReferences();
            progressiveYamlAddData();
        }
    }

    /**
     * A localizationScope that refers to a component variable that is not declared as a composite reference
     *
     * @throws Exception
     */
    @Test
    public void testProgressiveWithReferenceAndNoHierarchicalReferenceYaml() throws Exception {

        URL resource = getClass().getResource(fixtures.getProgressiveYaml().get("testAuthorizationScopeWithReferenceAndNoHierarchicalReference"));
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "progressive");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();
        }
        progressiveYamlAddReferences();
        progressiveYamlAddData();
    }

    private void progressiveYamlAddReferences() throws Exception {
        String response;
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getProgressiveYamlReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive/references/{refType}", e.getKey())
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
        for (Map.Entry<String, String> e : fixtures.getProgressiveYamlDataFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/progressive/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.fileId", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();
            }
        }
    }

    @Test
    public void testRecursivity() throws Exception {

        URL resource = getClass().getResource(fixtures.getRecursivityApplicationConfigurationResourceName());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            addUserRightCreateApplication(authUserId, "recursivite");

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");


            response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite")
                            .param("filter", "ALL")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.references.taxon.dynamicColumns['propriétés de taxons'].reference", IsEqual.equalTo("proprietes_taxon")))
                    .andExpect(jsonPath("$.references.taxon.dynamicColumns['propriétés de taxons'].headerPrefix", IsEqual.equalTo("pt_")))
                    .andExpect(jsonPath("$.internationalization.references.taxon.internationalizedDynamicColumns['propriétés de taxons'].en", IsEqual.equalTo("Properties of Taxa")))
                    .andReturn().getResponse().getContentAsString();

        }

        String response;
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getRecursiviteReferentielOrderFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        // Ajout de taxon
        for (Map.Entry<String, String> e : fixtures.getRecursiviteReferentielTaxon().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
        for (Map.Entry<String, String> e : fixtures.getRecursiviteReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }
    }

    private void testFilesAndDataOnServer(String plateforme, String projet, String site, int expected, int numberOfVersions, String fileUUID, boolean published) throws Exception {
        ResultActions resultActions = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                        .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(numberOfVersions)));

        if (published) {
            resultActions = resultActions.
                    andExpect(jsonPath("$[*][?(@.params.published == true )]", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$[*][?(@.params.published == true )].id").value(fileUUID));
        } else {
            resultActions = resultActions.
                    andExpect(jsonPath("$[*][?(@.params.published == true )]").isEmpty());
        }
        resultActions
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Category(ACBB_TEST.class)
    public void addApplicationAcbb() throws Exception {
        addUserRightCreateApplication(authUserId, "acbb");
        URL resource = getClass().getResource(fixtures.getAcbbApplicationConfigurationResourceName());
        assert resource != null;
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }

        addReferences();
        addDataBiomassProduction();
        addDataFluxTours();
        addDataSWC();
    }

    private void addUserRightCreateApplication(UUID userId, String pattern) throws Exception {
        ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                        .param("userIdOrLogin", userId.toString())
                        .param("applicationPattern", pattern)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(userId.toString())))
                .andExpect(jsonPath("$.roles.memberOf", Matchers.hasItem("applicationCreator")))
                .andExpect(jsonPath("$.authorizations", Matchers.hasItem(pattern)))
                .andExpect(jsonPath("$.id", IsEqual.equalTo(userId.toString())));
    }

    private void addDataSWC() throws Exception {
        try (InputStream in = fixtures.openSwcDataResourceName(true)) {
            MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            String actualJson = mockMvc.perform(get("/api/v1/applications/acbb/data/SWC")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
            Assert.assertEquals(2912, StringUtils.countMatches(actualJson, "\"SWC\":"));
        }

        {
            String actualCsv = mockMvc.perform(get("/api/v1/applications/acbb/data/SWC")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 50));
        }
    }

    private void addDataBiomassProduction() throws Exception {
        // ajout de data
        try (InputStream in = getClass().getResourceAsStream(fixtures.getBiomasseProductionTeneurDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        {
            String actualJson = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
            Assert.assertEquals(252, StringUtils.countMatches(actualJson, "prairie permanente"));
        }

        {
            String actualCsv = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 50));
        }
    }

    private void addDataFluxTours() throws Exception {
        // ajout de data
        try (InputStream in = getClass().getResourceAsStream(fixtures.getFluxToursDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/flux_tours")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        // restitution de data json
        {
//            String expectedJson = Resources.toString(getClass().getResource("/data/acbb/compare/export.json"), Charsets.UTF_8);
            String actualJson = mockMvc.perform(get("/api/v1/applications/acbb/data/flux_tours")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[? (@.values.date.datetime =~ /.*\\/2004.*/i)]", Matchers.hasSize(17568)))
                    .andExpect(jsonPath("$.rows[*].values.parcelle.chemin", Matchers.hasSize(17568)))
//                    .andExpect(content().json(expectedJson))
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
        }

        // restitution de data csv
        {
//            String expectedCsv = Resources.toString(getClass().getResource("/data/acbb/compare/export.csv"), Charsets.UTF_8);
            String actualCsv = mockMvc.perform(get("/api/v1/applications/acbb/data/flux_tours")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
//                    .andExpect(content().string(expectedCsv))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 50));
        }
    }

    private void addReferences() throws Exception {
        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getAcbbReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                JsonPath.parse(response).read("$.id");
            }
        }

        String getReferenceResponse = mockMvc.perform(get("/api/v1/applications/acbb/references/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        final GetReferenceResult refs = objectMapper.readValue(getReferenceResponse, GetReferenceResult.class);
        Assert.assertEquals(103, refs.getReferenceValues().size());

        String getReferenceCsvResponse = mockMvc.perform(get("/api/v1/applications/acbb/references/parcelles/csv")
                        .cookie(authCookie)
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Category(HAUTE_FREQUENCE_TEST.class)
    public void addApplicationHauteFrequence() throws Exception {
        addUserRightCreateApplication(authUserId, "hautefrequence");
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getHauteFrequenceApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getHauteFrequenceReferentielFiles().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getHauteFrequenceDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addDuplicatedTest() throws Exception {
        addUserRightCreateApplication(authUserId, "duplicated");
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getDuplicatedApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "duplicated.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        String message;

        //on charge le fichier de type zone d'étude
        final String typezonewithoutduplicationDuplication = fixtures.getDuplicatedReferentielFiles().get("typezonewithoutduplication");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude.csv", "text/plain", refStream);
            message = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on recharge le fichier de type zone d'étude
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithoutduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude2.csv", "text/plain", refStream);
            message = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone type d'étude avec une duplication
        final String typezonewithduplicationDuplication = fixtures.getDuplicatedReferentielFiles().get("typezonewithduplication");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(typezonewithduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "type_zone_etude_duplicate.csv", "text/plain", refStream);
            final ResultActions error = mockMvc
                    .perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
            //Assert.fail();
        } catch (NestedServletException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidDatasetContentException);
            final InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            final List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(4, errors.get(0).getLineNumber());
            final ValidationCheckResult validationCheckResult = errors.get(0).getValidationCheckResult();
            Assert.assertEquals(ValidationLevel.ERROR, validationCheckResult.getLevel());
            Assert.assertEquals("duplicatedLineInDatatype", validationCheckResult.getMessage());
            final Map<String, Object> messageParams = validationCheckResult.getMessageParams();
            Assert.assertEquals("types_de_zones_etudes", messageParams.get("file"));
            Assert.assertEquals(4, messageParams.get("lineNumber"));
            Assert.assertArrayEquals(new Integer[]{3, 4}, ((Set) messageParams.get("otherLines")).toArray());
            Assert.assertEquals("zone20", messageParams.get("duplicateKey"));
        }

        //il doit toujours y avoir le même nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "types_de_zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();
/*
on test le dépôt d'un fichier récursif
 */


//on charge le fichier de zone d'étude
        final String zonewithoutduplicationDuplication = fixtures.getDuplicatedReferentielFiles().get("zonewithoutduplication");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude.csv", "text/plain", refStream);
            message = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        // on vérifie le nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on recharge le fichier de zone d'étude
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithoutduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude2.csv", "text/plain", refStream);
            message = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
        }

        //il doit toujours y avoir le même nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();


        //on charge le fichier de zone d'étudeavec une duplication
        final String zonewithduplicationDuplication = fixtures.getDuplicatedReferentielFiles().get("zonewithduplication");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithduplicationDuplication)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_duplicated.csv", "text/plain", refStream);
            final ResultActions error = mockMvc
                    .perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
            //Assert.fail();
        } catch (NestedServletException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidDatasetContentException);
            final InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            final List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(4, errors.get(0).getLineNumber());
            final ValidationCheckResult validationCheckResult = errors.get(0).getValidationCheckResult();
            Assert.assertEquals(ValidationLevel.ERROR, validationCheckResult.getLevel());
            Assert.assertEquals("duplicatedLineInDatatype", validationCheckResult.getMessage());
            final Map<String, Object> messageParams = validationCheckResult.getMessageParams();
            Assert.assertEquals("zones_etudes", messageParams.get("file"));
            Assert.assertEquals(4, messageParams.get("lineNumber"));
            Assert.assertArrayEquals(new Integer[]{2, 4}, ((Set) messageParams.get("otherLines")).toArray());
            Assert.assertEquals("site1", messageParams.get("duplicateKey"));
        }

        //il doit toujours y avoir le même nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();

        //on charge le fichier de zone d'étudeavec une duplication
        final String zonewithmissingParent = fixtures.getDuplicatedReferentielFiles().get("zonewithmissingparent");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(zonewithmissingParent)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zone_etude_missing_parent.csv", "text/plain", refStream);
            final ResultActions error = mockMvc
                    .perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                            .file(refFile)
                            .cookie(authCookie));
            //Assert.fail();
        } catch (NestedServletException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidDatasetContentException);
            final InvalidDatasetContentException invalidDatasetContentException = (InvalidDatasetContentException) e.getCause();
            final List<CsvRowValidationCheckResult> errors = invalidDatasetContentException.getErrors();
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(3, errors.get(0).getLineNumber());
            final ValidationCheckResult validationCheckResult = errors.get(0).getValidationCheckResult();
            Assert.assertEquals(ValidationLevel.ERROR, validationCheckResult.getLevel());
            Assert.assertEquals("missingParentLineInRecursiveReference", validationCheckResult.getMessage());
            final Map<String, Object> messageParams = validationCheckResult.getMessageParams();
            Assert.assertEquals("zones_etudes", messageParams.get("references"));
            Assert.assertEquals(3L, messageParams.get("lineNumber"));
            Assert.assertEquals("site3", messageParams.get("missingReferencesKey"));
            Assert.assertTrue(Set.of("site3", "site1.site2", "site1", "site2").containsAll((Set) messageParams.get("knownReferences")));
        }

        //il doit toujours y avoir le même nombre de ligne
        message = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/duplicated/references/{refType}", "zones_etudes")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.referenceValues.length()", IsEqual.equalTo(2)))
                .andReturn().getResponse().getContentAsString();

        //on teste un dépot de fichier de données
        final String dataWithoutDuplicateds = fixtures.getDuplicatedDataFiles().get("data_without_duplicateds");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        //on teste le nombre de ligne
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4
                    )))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }

        // on  redepose le fichier

        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data_without_duplicateds.csv", "text/plain", refStream);
            final String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    //.andExpect(jsonPath("$[0].validationCheckResult.messageParams.file", IsEqual.equalTo("dty"))).andExpect(jsonPath("$[0].validationCheckResult.messageParams.failingRowContent", StringContains.containsString("1980-02-23")))
                    .andReturn().getResponse().getContentAsString();
        }
        // le nombre de ligne est inchangé
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4
                    )))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }
        //on teste un dépot de fichier de données avec lignes dupliquées
        final String dataWithDuplicateds = fixtures.getDuplicatedDataFiles().get("data_with_duplicateds");
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithDuplicateds)) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data_with_duplicateds.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$[0].validationCheckResult.message", IsEqual.equalTo("duplicatedLineInDatatype")))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(5)))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.duplicatedRows", CoreMatchers.hasItem(6)))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.Date_day", IsEqual.equalTo("24/02/1980")))
                    .andExpect(jsonPath("$[0].validationCheckResult.messageParams.uniquenessKey.localization_zones_etudes", IsEqual.equalTo("site1")));

        }
        // le nombre de ligne est inchangé
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows", IsEqual.equalTo(4
                    )))
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationOLAC() throws Exception {
        addUserRightCreateApplication(authUserId, "olac");
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getOlaApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        final String contentAsString = mockMvc.perform(get("/api/v1/applications/olac", "ALL,REFERENCETYPE")
                        .cookie(authCookie)
                        .param("filter", "ALL"))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse().getContentAsString();

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getOlaReferentielFiles().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getConditionPrelevementDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "condition_prelevements.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/condition_prelevements")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getPhysicoChimieDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "physico-chimie.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/physico-chimie")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getSondeDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "sonde_truncated.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/sonde_truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getPhytoAggregatedDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton_aggregated.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/phytoplancton_aggregated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getPhytoplanctonDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "phytoplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/phytoplancton__truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getZooplanctonDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton__truncated.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/zooplancton__truncated")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getZooplactonBiovolumDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "zooplancton_biovolumes.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/zooplancton_biovolumes")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationFORET_essai() throws Exception {
        addUserRightCreateApplication(authUserId, "foret");
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getForetEssaiApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "foret_essai.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getForetEssaiReferentielFiles().entrySet()) {
            log.debug(e.getKey());
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data
        for (Map.Entry<String, String> entry : fixtures.getForetEssaiDataResourceName().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(entry.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/" + entry.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

                if (entry.getKey().equals("swc_j")) {
                    authenticationService.setRoleAdmin();
                    final String responseForBuildSynthesis = mockMvc.perform(put("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .cookie(authCookie))
                            .andExpect(jsonPath("$.SWC", Matchers.hasSize(8)))
                            .andReturn().getResponse().getContentAsString();
                    final String responseForGetSynthesis = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/foret/synthesis/{refType}", entry.getKey())
                                    .cookie(authCookie))
                            .andExpect(jsonPath("$.SWC", Matchers.hasSize(8)))
                            .andExpect(jsonPath("$.SWC[*].aggregation", Matchers.containsInAnyOrder("10", "120", "160", "200", "250", "30", "55", "80")))
                            .andReturn().getResponse().getContentAsString();
                }
            }
        }
    }

    @Test
    @Category(OTHERS_TEST.class)
    public void addApplicationFORET() throws Exception {
        addUserRightCreateApplication(authUserId, "foret");
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getForetApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "foret.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getForetReferentielFiles().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getFluxMeteoForetDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
    }

    @Test
    @Ignore("utile comme benchmark, ne vérifie rien")
    public void benchmarkImportData() throws Exception {
        addApplicationAcbb();
        try (InputStream in = fixtures.openSwcDataResourceName(false)) {
            MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);
            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        }
    }
}