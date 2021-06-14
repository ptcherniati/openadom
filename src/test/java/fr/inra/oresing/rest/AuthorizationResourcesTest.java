package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class AuthorizationResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private Fixtures fixtures;

    private Cookie authCookie;

    @Before
    public void createApplication() throws Exception {
        authCookie = fixtures.addApplicationAcbb();
    }

    @Test
    public void testAddAuthorization() throws Exception {

        OreSiUser reader = authenticationService.createUser("UnReader", "xxxxxxxx");
        Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", "UnReader")
                .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        {
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(authCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertTrue("Le créateur de l'application doit pouvoir la retrouver dans la liste", response.contains("acbb"));
        }

        {
            String response = mockMvc.perform(get("/api/v1/applications")
                            .cookie(authReaderCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertFalse("On ne devrait pas voir l'application car les droits n'ont pas encore été accordés", response.contains("acbb"));
        }

        {
            mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                    .cookie(authReaderCookie)
                    .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
            String readerUserId = reader.getId().toString();
            String json = "{\"userId\":\"" + readerUserId + "\",\"applicationNameOrId\":\"acbb\",\"dataType\":\"biomasse_production_teneur\",\"dataGroup\":\"all\",\"localizationScope\":\"theix.theix__22\",\"fromDay\":[2010,1,1],\"toDay\":[2010,6,1]}";

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
            Assert.assertTrue("Une fois l'accès donné, on doit pouvoir avec l'application dans la liste", response.contains("acbb"));
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur")
                    .cookie(authReaderCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // contrôle sur la fenêtre temporelle
            Assert.assertFalse(json.contains("31/08/2010"));
            Assert.assertTrue(json.contains("26/05/2010"));

            // contrôle sur le groupe de données
//            Assert.assertFalse(json.contains("Couleur des individus"));
//            Assert.assertFalse(json.contains("Nombre d'individus"));
//            Assert.assertTrue(json.contains("date"));
//            Assert.assertTrue(json.contains("projet"));
//            Assert.assertTrue(json.contains("espece"));

            // contrôle sur la localization
            Assert.assertFalse(json.contains("theix.theix__7"));
            Assert.assertTrue(json.contains("theix.theix__22"));
        }
    }
}