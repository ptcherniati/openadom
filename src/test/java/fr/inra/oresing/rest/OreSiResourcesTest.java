package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private UUID userId;

    @Before
    public void createUser() throws Exception {
        userId = authenticationService.createUser("poussin", "xxxxxxxx").getUserId();
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
    }

    @Test
    public void addApplicationMonsore() throws Exception {
        String appId;

        URL resource = getClass().getResource(fixtures.getMonsoreApplicationConfigurationResourceName());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            // on n'a pas le droit de creer de nouvelle application
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError());
            authenticationService.addUserRightCreateApplication(userId);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .param("comment", "commentaire")
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            appId = JsonPath.parse(response).read("$.id");
        }

        String response = mockMvc.perform(get("/api/v1/applications/{appId}", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authCookie))
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

        String getReferencesResponse = mockMvc.perform(get("/api/v1/applications/monsore/references/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        GetReferenceResult GetReferenceResult = objectMapper.readValue(getReferencesResponse, GetReferenceResult.class);
        Assert.assertEquals(9, GetReferenceResult.getReferenceValues().size());

        // ajout de data
        resource = getClass().getResource(fixtures.getPemDataResourceName());
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(response, 50));
        }

        try (InputStream pem = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String data = IOUtils.toString(Objects.requireNonNull(pem), StandardCharsets.UTF_8);
            String wrongData = data.replace("plateforme", "entete_inconnu");
            byte[] bytes = wrongData.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", bytes);
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }

        // list des types de data
        response = mockMvc.perform(get("/api/v1/applications/monsore/data")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        log.debug(StringUtils.abbreviate(response, 50));

//        // creation d'un user qui aura le droit de lire les données
//        OreSiUser reader = authRepository.createUser("UnReader", "xxxxxxxx");
//        mockMvc.perform(put("/api/v1/applications/{nameOrId}/users/{role}/{userId}",
//                appId, ApplicationRight.READER.name(), reader.getId().toString())
////                .contentType(MediaType.APPLICATION_JSON)
//                .cookie(authCookie))
//                .andExpect(status().isOk());
//
//        Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
//                .param("login", "UnReader")
//                .param("password", "xxxxxxxx"))
//                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        // restitution de data json
        {
            String expectedJson = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.json")), Charsets.UTF_8);
            JSONArray jsonArray = new JSONArray(expectedJson);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.getString(i));
            }

            String actualJson = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.variables").isArray())
                    .andExpect(jsonPath("$.variables", Matchers.hasSize(6)))
                    .andExpect(jsonPath("$.variables").value(Stream.of("date", "projet", "site", "Couleur des individus", "Nombre d'individus", "espece").collect(Collectors.toList())))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.DateLineChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.IntegerChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.rows").isArray())
                    .andExpect(jsonPath("$.rows", Matchers.hasSize(306)))
                    //.andExpect(jsonPath("$.rows.value").value(list))
                    .andExpect(jsonPath("$.totalRows", Is.is(306)))
                    .andExpect(jsonPath("$.rows[*].values.date.value", Matchers.hasSize(306)))
                    .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'].unit", Matchers.hasSize(306)))
                    .andExpect(jsonPath("$.rows[*].values['Couleur des individus'].unit", Matchers.hasSize(306)))
                    .andReturn().getResponse().getContentAsString();
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
                            .cookie(authCookie)
                            .param("downloadDatasetQuery", filter)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows", Matchers.hasSize(9)))
                    .andExpect(jsonPath("$.rows[*].values.date[?(@.value == 'date:1984-01-01T00:00:00:01/01/1984')]", Matchers.hasSize(9)))
                    .andExpect(jsonPath("$.rows[*].values['Nombre d\\'individus'][?(@.value ==25)]", Matchers.hasSize(9)))
                    .andExpect(jsonPath("$.rows[*].values['Couleur des individus'][?(@.value =='couleur_des_individus__vert')]", Matchers.hasSize(9)))
                    .andExpect(jsonPath("$.rows[*].values.site.plateforme").value(Stream.of("a", "p1", "p1", "p1", "p1", "p1", "p1", "p2", "p2").collect(Collectors.toList())))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualJson, 50));

        }

        // restitution de data csv
        {
            String expectedCsv = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.csv")), Charsets.UTF_8);
            String actualCsv = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    //     .andExpect(content().string(expectedCsv))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 50));
            List<String> actualCsvToList = Arrays.stream(actualCsv.split("\r+\n"))
                    .collect(Collectors.toList());
            List<String> expectedCsvToList = Arrays.stream(expectedCsv.split("\r+\n"))
                    .collect(Collectors.toList());
            Assert.assertEquals(expectedCsvToList.size(), actualCsvToList.size());
            actualCsvToList.forEach(expectedCsvToList::remove);
            Assert.assertTrue(expectedCsvToList.isEmpty());
            Assert.assertEquals(306, StringUtils.countMatches(actualCsv, "/1984"));
        }

        try (InputStream in = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String csv = IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
            String invalidCsv = csv.replace("projet_manche", "projet_manch");
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", invalidCsv.getBytes(StandardCharsets.UTF_8));
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
            Assert.assertTrue(response.contains("projet_manch"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("141"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("142"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("143"));
            Assert.assertTrue("Il faut mentionner les lignes en erreur", response.contains("310"));
        }

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

    @Test
    public void addApplicationMonsoreWithRepository() throws Exception {
        URL resource = getClass().getResource(fixtures.getMonsoreApplicationConfigurationResourceName());
        String oirFilesUUID;
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            authenticationService.addUserRightCreateApplication(userId);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
        }

        String response = null;
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
        // ajout de data
        String projet = "manche";
        String plateforme = "plateforme";
        String site = "oir";
        resource = getClass().getResource(fixtures.getPemRepositoryDataResourceName(projet, site));

        // on dépose 3 fois le même fichier sans le publier
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);

            //fileOrUUID.binaryFileDataset/applications/{name}/file/{id}
            for (int i = 0; i < 3; i++) {
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                                .file(refFile)
                                .param("params", fixtures.getPemRepositoryParams(projet, plateforme, site, false))
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn().getResponse().getContentAsString();
            }
            log.debug(response);
            //on regarde les versions déposées
            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(authCookie))
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


            // on publie le dernier fichier déposé

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .param("params", fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, true))
                            .cookie(authCookie))
                    // .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            // on récupère la liste des versions déposées

            response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                            .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                            .cookie(authCookie))
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
        }
        //on publie 4 fichiers

        publishOrDepublish("manche", "plateforme", "scarff", 68, true, 1, true);
        publishOrDepublish("atlantique", "plateforme", "scarff", 34, true, 1, true);
        publishOrDepublish("atlantique", "plateforme", "nivelle", 34, true, 1, true);
        publishOrDepublish("manche", "plateforme", "nivelle", 34, true, 1, true);
        //on publie une autre version
        String fileUUID = publishOrDepublish("manche", "plateforme", "nivelle", 34, true, 2, true);
        // on supprime l'application publiée
        response = mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/applications/monsore/file/" + fileUUID)
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));
        testFilesAndDataOnServer(plateforme, "manche", "nivelle", 0, 1, fileUUID, false);


        // on depublie le fichier oir déposé

        response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                        .param("params", fixtures.getPemRepositoryParamsWithId(projet, plateforme, site, oirFilesUUID, false))
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère la liste des versions déposées

        response = mockMvc.perform(get("/api/v1/applications/monsore/filesOnRepository/pem")
                        .param("repositoryId", fixtures.getPemRepositoryId(plateforme, projet, site))
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", Matchers.hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == false )]", Matchers.hasSize(3)))
                .andExpect(jsonPath("$[*][?(@.params.published == true )]", Matchers.hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));

        // on récupère le data en base

        response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                        .cookie(authCookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.totalRows").value(136))
                .andExpect(jsonPath("$.rows[*]", Matchers.hasSize(136)))
                .andExpect(jsonPath("$.rows[*].values[? (@.site.chemin == 'oir__p1')][? (@.projet.value == 'projet_manche')]", Matchers.hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        log.debug(StringUtils.abbreviate(response, 50));
        // on supprime le fic
    }

    private String publishOrDepublish(String projet, String plateforme, String site, int expected, boolean toPublish, int numberOfVersions, boolean published) throws Exception {
        URL resource;
        String response;
        resource = getClass().getResource(fixtures.getPemRepositoryDataResourceName(projet, site));
        try (InputStream refStream = Objects.requireNonNull(resource).openStream()) {

            //dépôt et publication d'un fichier projet site__p1
            MockMultipartFile refFile = new MockMultipartFile("file", String.format("%s-%s-p1-pem.csv", projet, site), "text/plain", refStream);
            refFile.transferTo(Path.of("/tmp/pem.csv"));
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .param("params", fixtures.getPemRepositoryParams(projet, plateforme, site, toPublish))
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            String fileUUID = JsonPath.parse(response).read("$.fileId");

            //liste des fichiers projet/site
            testFilesAndDataOnServer(plateforme, projet, site, expected, numberOfVersions, fileUUID, published);
            log.debug(StringUtils.abbreviate(response, 50));
            return fileUUID;
        }
    }

    @Test
    public void testRecursivity() throws Exception {

        URL resource = getClass().getResource(fixtures.getRecursivityApplicationConfigurationResourceName());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
            //définition de l'application
            authenticationService.addUserRightCreateApplication(userId);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");
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
    public void addApplicationAcbb() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);

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

        GetReferenceResult refs = objectMapper.readValue(getReferenceResponse, GetReferenceResult.class);
        Assert.assertEquals(103, refs.getReferenceValues().size());

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

        getReferenceResponse = mockMvc.perform(get("/api/v1/applications/acbb/references/parcelles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        refs = objectMapper.readValue(getReferenceResponse, GetReferenceResult.class);
        Assert.assertEquals(103, refs.getReferenceValues().size());

        String getReferenceCsvResponse = mockMvc.perform(get("/api/v1/applications/acbb/references/parcelles/csv")
                        .cookie(authCookie)
                        .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assert.assertEquals(31, StringUtils.countMatches(getReferenceCsvResponse, "theix"));

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
//                    .andExpect(content().json(expectedJson))
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 50));
            Assert.assertEquals(17568, StringUtils.countMatches(actualJson, "/2004"));
            Assert.assertTrue(actualJson.contains("laqueuille.laqueuille__1"));
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
            Assert.assertEquals(17568, StringUtils.countMatches(actualCsv, "/2004"));
            Assert.assertTrue(actualCsv.contains("Parcelle"));
            Assert.assertTrue(actualCsv.contains("laqueuille;1"));
        }

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
            Assert.assertEquals(252, StringUtils.countMatches(actualCsv, "prairie permanente"));
        }

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
            Assert.assertEquals(1456, StringUtils.countMatches(actualCsv, "/2010"));
        }
    }

    @Test
    public void addApplicationHauteFrequence() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
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
    public void addDuplicatedTest() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
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
            Assert.fail();
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
            Assert.fail();
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
            Assert.fail();
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
            Assert.assertTrue(Set.of("site3","site1.site2","site1","site2").containsAll((Set) messageParams.get("knownReferences")));
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
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/duplicated/data/dty")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        // le nombre de ligne est inchangé
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(dataWithoutDuplicateds)) {
            final String response = mockMvc.perform(get("/api/v1/applications/duplicated/data/dty")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.totalRows", IsEqual.equalTo(8
                    )))
                .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }
    }

    @Test
    public void addApplicationOLAC() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getOlaApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

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
    public void addApplicationFORET_essai() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
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
        for (Map.Entry<String, String> entry : fixtures.getFluxMeteoForetEssaiDataResourceName().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(entry.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/" + entry.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }
    }

    @Test
    public void addApplicationFORET() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
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