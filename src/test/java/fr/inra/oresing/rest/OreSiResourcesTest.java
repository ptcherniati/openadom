package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthRepository;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
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
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class,
        FlywayTestExecutionListener.class})
@Rollback
@FlywayTest
public class OreSiResourcesTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Test
    public void addApplication() throws Exception {
        String appId;

        OreSiUser user = authRepository.createUser("poussin", "xxxxxxxx");

        Cookie authCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", "poussin")
                .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHandler.JWT_TOKEN);

        URL resource = getClass().getResource("/data/monsore.yaml");
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


        String response = mockMvc.perform(get("/api/v1/applications/" + appId)
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


        Map<String, String> referentielFiles = new HashMap<>();
        referentielFiles.put("especes", "especes.csv");
        referentielFiles.put("projet", "projet.csv");
        referentielFiles.put("sites", "sites.csv");
        referentielFiles.put("themes", "themes.csv");
        referentielFiles.put("type de fichiers", "type_de_fichiers.csv");
        referentielFiles.put("type_de_sites", "type_de_sites.csv");
        referentielFiles.put("types_de_donnees_par_themes_de_sites_et_projet", "types_de_donnees_par_themes_de_sites_et_projet.csv");
        referentielFiles.put("unites", "unites.csv");
        referentielFiles.put("valeurs_qualitatives", "valeurs_qualitatives.csv");
        referentielFiles.put("variables", "variables.csv");
        referentielFiles.put("variables_et_unites_par_types_de_donnees", "variables_et_unites_par_types_de_donnees.csv");

        // Ajout de referentiel
        for (Map.Entry<String, String> e : referentielFiles.entrySet()) {
            resource = getClass().getResource("/data/refdatas/" + e.getValue());
            try (InputStream refStream = resource.openStream()) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/" + e.getKey())
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
        resource = getClass().getResource("/data/data-pem.csv");
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

        // restitution de data json
        resource = getClass().getResource("/data/compare/export.json");
        try (InputStream in = resource.openStream()){
            String jsonCompare = new String(in.readAllBytes());
            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir")
                    .cookie(authCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().json(jsonCompare))
                    .andReturn().getResponse().getContentAsString();
        }

        // restitution de data csv
        resource = getClass().getResource("/data/compare/export.csv");
        try (InputStream in = resource.openStream()) {
            String csvCompare = new String(in.readAllBytes());
            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir")
                    .cookie(authCookie)
                    .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andExpect(content().string(csvCompare))
                    .andReturn().getResponse().getContentAsString();
        }

        // restitution de data csv
        resource = getClass().getResource("/data/compare/exportColumn.csv");
        try (InputStream in = resource.openStream()) {
            String csvCompare = new String(in.readAllBytes());
            response = mockMvc.perform(get("/api/v1/applications/monsore/data/pem?projet=Projet atlantique&site=oir&outColumn=date;espece;plateforme;Nombre d'individus")
                    .cookie(authCookie)
                    .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andExpect(content().string(csvCompare))
                    .andReturn().getResponse().getContentAsString();
        }

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

    boolean deepFieldEquals(Object o1, Object o2, String... excludes) {
        Map<String, Object> map1 = objectMapper.convertValue(o1, new TypeReference<Map>() {
        });
        Map<String, Object> map2 = objectMapper.convertValue(o2, new TypeReference<Map>() {
        });
        Stream.of(excludes)
                .peek(map1::remove)
                .forEach(map2::remove);
        return map1.equals(map2);
    }
}