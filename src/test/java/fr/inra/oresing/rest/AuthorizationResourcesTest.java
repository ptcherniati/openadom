package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthRepository;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext
@Slf4j
public class AuthorizationResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private Fixtures fixtures;

    private Cookie authCookie;

    @Before
    public void createApplication() throws Exception {
        String aPassword = "xxxxxxxx";
        String aLogin = "poussin";
        OreSiUser user = authRepository.createUser(aLogin, aPassword);
        authRepository.addUserRightCreateApplication(user.getId());
        authCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", aLogin)
                .param("password", aPassword))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        try (InputStream configurationFile = getClass().getResourceAsStream(fixtures.getMonsoreApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : fixtures.getMonsoreReferentielFiles().entrySet()) {
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
    }

    @Test
    public void testAddAuthorization() throws Exception {

        OreSiUser reader = authRepository.createUser("UnReader", "xxxxxxxx");
        Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", "UnReader")
                .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        {
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(authCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertTrue("Le créateur de l'application doit pouvoir la retrouver dans la liste", response.contains("monsore"));
        }

        {
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(authReaderCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertFalse("On ne devrait pas voir l'application car les droits n'ont pas encore été accordés", response.contains("monsore"));
        }

        {
            mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                    .cookie(authReaderCookie)
                    .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
            String readerUserId = reader.getId().toString();
            String json = "{\"userId\":\"" + readerUserId + "\",\"applicationNameOrId\":\"monsore\",\"dataset\":\"pem\",\"dataGroup\":\"quantitatif\",\"referenceIds\":null,\"fromDay\":[1984,1,2],\"toDay\":[1984,1,3]}";

            MockHttpServletRequestBuilder create = post("/api/v1/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);
        }

        {
            String response = mockMvc.perform(get("/api/v1/applications")
                    .cookie(authReaderCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertTrue("Une fois l'accès donné, on doit pouvoir avec l'application dans la liste", response.contains("monsore"));
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/monsore/data/pem")
                    .cookie(authReaderCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // contrôle sur la fenêtre temporelle
//            Assert.assertFalse(json.contains("01/01/1984"));
//            Assert.assertTrue(json.contains("02/01/1984"));
//            Assert.assertTrue(json.contains("03/01/1984"));
//            Assert.assertFalse(json.contains("04/01/1984"));

            // contrôle sur le groupe de données
            Assert.assertFalse(json.contains("Couleur des individus"));
            Assert.assertTrue(json.contains("Nombre d'individus"));
        }
    }
}