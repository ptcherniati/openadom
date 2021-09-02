package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            // on a pas le droit de creer de nouvelle application
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError());
            authenticationService.addUserRightCreateApplication(userId);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                            .file(configuration)
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

                String refFileId = JsonPath.parse(response).read("$.id");
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
        try (InputStream refStream = resource.openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(response);
        }

        try (InputStream pem = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String data = IOUtils.toString(pem, StandardCharsets.UTF_8);
            String wrongData = data.replace("plateforme", "entete_inconnu");
            byte[] bytes = wrongData.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", bytes);
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        } catch (IOException e) {
            throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
        }

        // list des type de data
        response = mockMvc.perform(get("/api/v1/applications/monsore/data")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        log.debug(response);

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
            String expectedJson = Resources.toString(getClass().getResource("/data/monsore/compare/export.json"), Charsets.UTF_8);
            JSONArray jsonArray = new JSONArray(expectedJson);
            List list = new ArrayList<String>();
            for (int i=0; i<jsonArray.length(); i++) {
                list.add( jsonArray.getString(i) );
            }

            String actualJson = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.variables").isArray())
                    .andExpect(jsonPath("$.variables", Matchers.hasSize(6)))
                    .andExpect(jsonPath("$.variables").value(Stream.of("date","site","Couleur des individus","Nombre d'individus","projet","espece").collect(Collectors.toList())))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.DateLineChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.ReferenceLineChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.checkedFormatVariableComponents.IntegerChecker", IsNull.notNullValue()))
                    .andExpect(jsonPath("$.rows").isArray())
                    .andExpect(jsonPath("$.rows", Matchers.hasSize(306)))
                    //.andExpect(jsonPath("$.rows.value").value(list))
                    .andExpect(jsonPath("$.totalRows", Is.is(306)))
                    .andReturn().getResponse().getContentAsString();
            log.debug(actualJson);
            Assert.assertEquals(306, StringUtils.countMatches(actualJson, "/1984"));
            Assert.assertEquals(306 * 2, StringUtils.countMatches(actualJson, "sans_unite"));

        }
        /**
         *  restitution de data json ajout de filtres et de tri
         * filtre :
         *  date.value between  '01/01/1984' and '01/01/1984'
         *  Nombre d\\'individus'.value between 20 and 29 (==25)
         *  Couleur des individus.value == 'couleur_des_individus__vert'
         *
         *  tri:
         *      par site.plateforme -> a < p1 < p2
         *
         */
        {
            String filter = "{\"application\":null,\"applicationNameOrId\":null,\"dataType\":null,\"offset\":null,\"limit\":15,\"variableComponentSelects\":[],\"variableComponentFilters\":[{\"variableComponentKey\":{\"variable\":\"date\",\"component\":\"value\"},\"filter\":null,\"type\":\"date\",\"format\":\"dd/MM/yyyy\",\"intervalValues\":{\"from\":\"1984-01-01\",\"to\":\"1984-01-01\"}},{\"variableComponentKey\":{\"variable\":\"Nombre d'individus\",\"component\":\"value\"},\"filter\":null,\"type\":\"numeric\",\"format\":\"integer\",\"intervalValues\":{\"from\":\"20\",\"to\":\"29\"}},{\"variableComponentKey\":{\"variable\":\"Couleur des individus\",\"component\":\"value\"},\"filter\":\"vert\",\"type\":\"reference\",\"format\":\"uuid\",\"intervalValues\":null}],\"variableComponentOrderBy\":[{\"variableComponentKey\":{\"variable\":\"site\",\"component\":\"plateforme\"},\"order\":\"ASC\",\"type\":null,\"format\":null}]}";
            String expectedJson = Resources.toString(getClass().getResource("/data/monsore/compare/export.json"), Charsets.UTF_8);
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
            log.debug(actualJson);

        }

        // restitution de data csv
        {
            String expectedCsv = Resources.toString(getClass().getResource("/data/monsore/compare/export.csv"), Charsets.UTF_8);
            String actualCsv = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    //     .andExpect(content().string(expectedCsv))
                    .andReturn().getResponse().getContentAsString();
            log.debug(actualCsv);
            List<String> actualCsvToList = Arrays.stream(actualCsv.split("\r+\n"))
                    .collect(Collectors.toList());
            List<String> expectedCsvToList = Arrays.stream(expectedCsv.split("\r+\n"))
                    .collect(Collectors.toList());
            Assert.assertEquals(expectedCsvToList.size(), actualCsvToList.size());
            actualCsvToList.forEach(l -> expectedCsvToList.remove(l));
            Assert.assertEquals(true, expectedCsvToList.isEmpty());
            Assert.assertEquals(306, StringUtils.countMatches(actualCsv, "/1984"));
        }

        try (InputStream in = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            String csv = IOUtils.toString(in, StandardCharsets.UTF_8);
            String invalidCsv = csv.replace("projet_manche", "projet_manch");
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", invalidCsv.getBytes(StandardCharsets.UTF_8));
            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
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
//        resource = getClass().getResource("/data/monsore-bad.yaml");
//        try (InputStream in = resource.openStream()) {
//            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);
//
//            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/configuration")
//                    .file(configuration)
//                    .cookie(authCookie))
//                    .andExpect(status().isCreated())
//                    .andReturn().getResponse().getContentAsString();
//        }
//
//
//        // ajout de data (echoue)
//        resource = getClass().getResource("/data/data-pem.csv");
//        try (InputStream refStream = resource.openStream()) {
//            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
//
//            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
//                    .file(refFile)
//                    .cookie(authCookie))
//                    .andExpect(status().is4xxClientError())
//                    .andReturn().getResponse().getContentAsString();
//
//            log.debug(response);
//        }
    }

    @Test
    public void addApplicationAcbb() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);

        String appId;
        URL resource = getClass().getResource(fixtures.getAcbbApplicationConfigurationResourceName());
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            appId = JsonPath.parse(response).read("$.id");
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

                String refFileId = JsonPath.parse(response).read("$.id");
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

                String refFileId = JsonPath.parse(response).read("$.id");
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

            log.debug(response);
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

            log.debug(StringUtils.abbreviate(actualJson, 500));
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
            log.debug(StringUtils.abbreviate(actualCsv, 500));
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

            log.debug(response);
        }

        {
            String actualJson = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 500));
            Assert.assertEquals(252, StringUtils.countMatches(actualJson, "prairie permanente"));
        }

        {
            String actualCsv = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 500));
            Assert.assertEquals(252, StringUtils.countMatches(actualCsv, "prairie permanente"));
        }

        try (InputStream in = fixtures.openSwcDataResourceName(true)) {
            MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(response);
        }

        {
            String actualJson = mockMvc.perform(get("/api/v1/applications/acbb/data/SWC")
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(actualJson, 500));
            Assert.assertEquals(2912, StringUtils.countMatches(actualJson, "\"SWC\":"));
        }

        {
            String actualCsv = mockMvc.perform(get("/api/v1/applications/acbb/data/SWC")
                            .cookie(authCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(actualCsv, 500));
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
    public void addApplicationPRO() throws Exception {
        authenticationService.addUserRightCreateApplication(userId);
        try (InputStream configurationFile = fixtures.getClass().getResourceAsStream(fixtures.getProApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "pro.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getProReferentielFiles().entrySet()) {
            try (InputStream refStream = fixtures.getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data PrelevementPro
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getPrelevementProDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "donnees_prelevement_pro.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/data/donnees_prelevement_pro")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // ajout de data PhysicoChimieSols
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getPhysicoChimieSolsProDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "physico_chimie_sols.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/data/physico_chimie_sols")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
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
        try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getdFluxMeteoForetDataResourceName())) {
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
            log.debug(response);
        }
    }
}
