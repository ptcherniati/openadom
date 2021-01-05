package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthRepository;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class})
public class OreSiResourcesTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private Fixtures fixtures;

    @Test
    public void addApplication() throws Exception {
        String appId;

        OreSiUser user = authRepository.createUser("poussin", "xxxxxxxx");

        Cookie authCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", "poussin")
                .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        URL resource = getClass().getResource(fixtures.getApplicationConfigurationResourceName());
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            // on a pas le droit de creer de nouvelle application
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(status().is4xxClientError());

            authRepository.addUserRightCreateApplication(user.getId());

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

        Application app2 = objectMapper.readValue(response, Application.class);

        Date now = new Date();
        Assert.assertEquals("monsore", app2.getName());
        Assert.assertEquals(List.of("especes", "projet", "sites", "themes", "type de fichiers", "type_de_sites", "types_de_donnees_par_themes_de_sites_et_projet", "unites", "valeurs_qualitatives", "variables", "variables_et_unites_par_types_de_donnees"), app2.getReferenceType());
        Assert.assertEquals(List.of("pem"), app2.getDataType());

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getReferentielFiles().entrySet()) {
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

        response = mockMvc.perform(get("/api/v1/applications/monsore/references/especes/esp_nom")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        List refs = objectMapper.readValue(response, List.class);
        Assert.assertFalse(refs.isEmpty());

        // ajout de data
        resource = getClass().getResource(fixtures.getPemDataResourceName());
        try (InputStream refStream = resource.openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            System.out.println(response);
        }

        // list des type de data
        response = mockMvc.perform(get("/api/v1/applications/monsore/data")
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        System.out.println(response);

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
            String expectedJson = Resources.toString(getClass().getResource("/data/compare/export.json"), Charsets.UTF_8);
            String actualJson = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                    .cookie(authCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().json(expectedJson))
                    .andReturn().getResponse().getContentAsString();
        }

        // restitution de data csv
        {
            String expectedCsv = Resources.toString(getClass().getResource("/data/compare/export.csv"), Charsets.UTF_8);
            String actualCsv = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                    .cookie(authCookie)
                    .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andExpect(content().string(expectedCsv))
                    .andReturn().getResponse().getContentAsString();
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
        resource = getClass().getResource("/data/monsore-bad.yaml");
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/configuration")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
        }


        // ajout de data (echoue)
        resource = getClass().getResource("/data/data-pem.csv");
        try (InputStream refStream = resource.openStream()) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);

            response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(status().is4xxClientError())
                    .andReturn().getResponse().getContentAsString();

            System.out.println(response);
        }
    }

}