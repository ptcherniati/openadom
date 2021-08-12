package fr.inra.oresing.rest;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    public void testAddAuthorization() throws Exception {
        Cookie authCookie = fixtures.addApplicationAcbb();
        CreateUserResult createUserResult = authenticationService.createUser("UnReader", "xxxxxxxx");
        String readerUserId = createUserResult.getUserId().toString();
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
            String response = mockMvc.perform(get("/api/v1/applications/acbb/dataType/biomasse_production_teneur/grantable")
                    .cookie(authCookie)
            ).andReturn().getResponse().getContentAsString();
            Assert.assertTrue(response.contains("lusignan"));
            Assert.assertTrue(response.contains("laqueuille.laqueuille__1"));
        }

        {
            String json = "{\"userId\":\"" + readerUserId + "\",\"applicationNameOrId\":\"acbb\",\"dataType\":\"biomasse_production_teneur\",\"dataGroup\":\"all\",\"authorizedScopes\":{\"localization\":\"theix.theix__22\"},\"fromDay\":[2010,1,1],\"toDay\":[2010,6,1]}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/acbb/dataType/biomasse_production_teneur/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
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

    @Test
    public void testAddAuthorizationOnTwoScopes() throws Exception {

        Cookie authCookie = fixtures.addApplicationHauteFrequence();

        CreateUserResult createUserResult = authenticationService.createUser("UnReader", "xxxxxxxx");
        String readerUserId = createUserResult.getUserId().toString();
        Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
                .param("login", "UnReader")
                .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        String authorizationId;

        {
            String json = "{\"userId\":\"" + readerUserId + "\",\"applicationNameOrId\":\"hautefrequence\",\"dataType\":\"hautefrequence\",\"dataGroup\":\"all\",\"authorizedScopes\":{\"localization\":\"bimont.bim13\",\"projet\":\"sou\"},\"fromDay\":[2016,1,1],\"toDay\":[2017,1,1]}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(response);

            authorizationId = JsonPath.parse(response).read("$.authorizationId");
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization/" + authorizationId)
                    .cookie(authCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(json);

            Assert.assertTrue(json.contains("[2016,1,1]"));
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization/")
                    .cookie(authCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(json);

            Assert.assertTrue(json.contains("[2016,1,1]"));
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence")
                    .cookie(authReaderCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // contrôle sur la fenêtre temporelle
            Assert.assertFalse(json.contains("30/01/2017"));
            Assert.assertTrue(json.contains("14/06/2016"));

            // contrôle sur la localisation
            Assert.assertFalse(json.contains("bimont.bim14"));
            Assert.assertTrue(json.contains("bimont.bim13"));

            // contrôle sur le projet
            Assert.assertFalse(json.contains("rnt"));
            Assert.assertTrue(json.contains("sou"));
        }

        {
            String json = mockMvc.perform(delete("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization/" + authorizationId)
                    .cookie(authCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(json);

        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence")
                    .cookie(authReaderCookie)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            Assert.assertEquals("{\"variables\":[],\"rows\":[]}", json);
        }
    }
}