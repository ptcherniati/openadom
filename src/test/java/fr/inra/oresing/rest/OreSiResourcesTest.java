package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiUtils;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.ReferenceValue;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void addApplication() throws Exception {
        URL resource = getClass().getResource("/data/monsore.yaml");
        try (InputStream in = resource.openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", in);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/applications/monsore")
                    .file(configuration))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            String appId = JsonPath.parse(response).read("$.id");

            response = mockMvc.perform(get("/applications/" + appId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // id
                    .andExpect(jsonPath("$.id", Is.is(appId)))
                    .andReturn().getResponse().getContentAsString();

            Application app2 = objectMapper.readValue(response, Application.class);

            Date now = new Date();
            Assert.assertEquals("monsore", app2.getName());
            Assert.assertEquals(List.of("especes","projet","sites","themes","type de fichiers","type_de_sites","types_de_donnees_par_themes_de_sites_et_projet","unites","valeurs_qualitatives","variables","variables_et_unites_par_types_de_donnees"), app2.getReferenceType());
            Assert.assertEquals(List.of("pem"), app2.getDataType());


            // Ajout de referentiel
            resource = getClass().getResource("/data/refdatas/especes.csv");
            try (InputStream refStream = resource.openStream()) {
                MockMultipartFile refFile = new MockMultipartFile("file", "especes.csv", "text/plain", refStream);

                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/applications/monsore/references/especes")
                        .file(refFile))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                        .andReturn().getResponse().getContentAsString();

                String refFileId = JsonPath.parse(response).read("$.id");

                response = mockMvc.perform(get("/applications/monsore/references/especes/esp_nom")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsString();

                System.out.println(response);
                List refs = objectMapper.readValue(response, List.class);
                System.out.println(refs);
            }
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