package fr.inra.oresing.rest;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthRepository;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Assert;
import org.junit.Before;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class,
        FlywayTestExecutionListener.class})
@Rollback
@FlywayTest
public class RelationalServiceTest {

    @Autowired
    private RelationalService relationalService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private Fixtures fixtures;

    private OreSiUserRequestClient applicationCreatorRequestClient;

    private OreSiRequestClient restrictedReaderRequestClient;

    @Before
    public void createApplication() throws Exception {
        String aPassword = "xxxxxxxx";
        String aLogin = "poussin";
        OreSiUser user = authRepository.createUser(aLogin, aPassword);
        applicationCreatorRequestClient = OreSiUserRequestClient.of(user.getId(), authRepository.getUserRole(user));
        authRepository.addUserRightCreateApplication(user.getId());
        Cookie authCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", aLogin)
                .param("password", aPassword))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().isCreated());
            }
        }

        // ajout de data
        try (InputStream refStream = getClass().getResourceAsStream(fixtures.getPemDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        OreSiUser reader = authRepository.createUser("UnReader", "xxxxxxxx");
        mockMvc.perform(put("/api/v1/applications/{nameOrId}/users/{role}/{userId}",
                fixtures.getApplicationName(), ApplicationRight.READER.name(), reader.getId().toString())
                .cookie(authCookie))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/applications/monsore/references/especes?esp_nom=LPF")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        String refId = JsonPath.parse(response).read("$[0].id");

        OreSiUser restrictedReader = authRepository.createUser("UnPetitReader", "xxxxxxxx");
        restrictedReaderRequestClient = OreSiUserRequestClient.of(restrictedReader.getId(), authRepository.getUserRole(restrictedReader));
        mockMvc.perform(put("/api/v1/applications/{nameOrId}/users/{role}/{userId}",
                fixtures.getApplicationName(), ApplicationRight.RESTRICTED_READER.name(), restrictedReader.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(authCookie)
                .content("[\"" + refId + "\"]"))
                .andExpect(status().isOk());
    }

    @Test
    public void testCreateViews() {
        OreSiApiRequestContext.get().setRequestClient(applicationCreatorRequestClient);

        relationalService.createViews(fixtures.getApplicationName());

        List<Map<String, Object>> viewContent = relationalService.readView();
        Assert.assertEquals(306, viewContent.size());

        // TODO brendan 24/11/2020 vérifier qu'on a pas de référentiel LPF
//        OreSiApiRequestContext.get().setRequestClient(restrictedReaderRequestClient);
//        List<Map<String, Object>> restrictedViewContent = relationalService.readView();

    }
}