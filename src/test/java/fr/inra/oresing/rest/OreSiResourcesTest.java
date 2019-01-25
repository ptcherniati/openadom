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

import java.util.Date;
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

        MockMultipartFile firstFile = new MockMultipartFile("data", "filename.txt", "text/plain", "some csv".getBytes());

        String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/files")
                        .file(firstFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String fileId = JsonPath.parse(response).read("$.id");

        Application app = new Application();
        app.setName("monsore");
        app.setConfig(UUID.fromString(fileId));

        response = mockMvc.perform(post("/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // id
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
        Assert.assertNotNull(app2.getCreationDate());
        Assert.assertNotNull(app2.getUpdateDate());
        Assert.assertTrue(deepFieldEquals(app, app2, OreSiUtils.fieldsOf(app::getCreationDate, app::getUpdateDate)));
    }

    boolean deepFieldEquals(Object o1, Object o2, String ... excludes) {
        Map<String, Object> map1 = objectMapper.convertValue(o1, new TypeReference<Map>() { });
        Map<String, Object> map2 = objectMapper.convertValue(o2, new TypeReference<Map>() { });
        Stream.of(excludes)
                .peek(map1::remove)
                .forEach(map2::remove);
        return map1.equals(map2);
    }
}