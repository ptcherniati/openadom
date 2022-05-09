package fr.inra.oresing.rest;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.Cookie;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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
            String json = "{\n" +
                    "   \"usersId\":[\"" + readerUserId + "\"],\n" +
                    "   \"applicationNameOrId\":\"acbb\",\n" +
                    "   \"id\": null,\n" +
                    "   \"name\": \"une authorization sur acbb\",\n" +
                    "   \"dataType\":\"biomasse_production_teneur\",\n" +
                    "   \"authorizations\":{\n" +
                    "   \"extraction\":[\n" +
                    "      {\n" +
                    "         \"requiredauthorizations\":{\n" +
                    "            \"localization\":\"theix.theix__22\"\n" +
                    "         },\n" +
                    "         \"dataGroup\":[\n" +
                    "            \"all\"\n" +
                    "         ],\n" +
                    "         \"intervalDates\":{\n" +
                    "            \"fromDay\":[\n" +
                    "               2010,\n" +
                    "               1,\n" +
                    "               1\n" +
                    "            ],\n" +
                    "            \"toDay\":[\n" +
                    "               2010,\n" +
                    "               6,\n" +
                    "               1\n" +
                    "            ]\n" +
                    "         }\n" +
                    "      }\n" +
                    "   ]\n" +
                    "}\n" +
                    "}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/acbb/dataType/biomasse_production_teneur/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
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
                    .andExpect(jsonPath("$.rows[*].values.parcelle.chemin").value(hasItemInArray(equalTo("theix.theix__22")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(not(hasItemInArray(equalTo("theix.theix__7"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(hasItemInArray(endsWith("26/05/2010")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(not(hasItemInArray(endsWith("31/08/2010"))), String[].class))

                    .andReturn().getResponse().getContentAsString();
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

            String json = "{\n" +
                    "   \"usersId\":[\"" + readerUserId + "\"],\n" +
                    "   \"applicationNameOrId\":\"hautefrequence\",\n" +
                    "   \"id\": null,\n" +
                    "   \"name\": \"une authorization sur haute fréquence\",\n" +
                    "   \"dataType\":\"hautefrequence\",\n" +
                    "   \"authorizations\":{\n" +
                    "   \"extraction\":[\n" +
                    "      {\n" +
                    "         \"requiredauthorizations\":{\n" +
                    "            \"localization\":\"bimont.bim13\",\n" +
                    "            \"projet\":\"sou\"\n" +
                    "         },\n" +
                    "         \"dataGroup\":[\n" +
                    "            \"all\"\n" +
                    "         ],\n" +
                    "         \"intervalDates\":{\n" +
                    "            \"fromDay\":[\n" +
                    "               2016,\n" +
                    "               1,\n" +
                    "               1\n" +
                    "            ],\n" +
                    "            \"toDay\":[\n" +
                    "               2017,\n" +
                    "               1,\n" +
                    "               1\n" +
                    "            ]\n" +
                    "         }\n" +
                    "      }\n" +
                    "   ]\n" +
                    "}\n" +
                    "}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            authorizationId = JsonPath.parse(response).read("$.authorizationId");
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization/" + authorizationId)
                            .cookie(authCookie))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(json, 50));

            Assert.assertTrue(json.contains("[2016,1,1]"));
        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .cookie(authReaderCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(hasItemInArray(equalTo("bimont.bim13")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(not(hasItemInArray(equalTo("bimont.bim14"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.projet").value(hasItemInArray(equalTo("sou")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.projet").value(not(hasItemInArray(equalTo("rnt"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.date.day").value(hasItemInArray(equalTo("date:2016-06-14T00:00:00:14/06/2016")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.date.day").value(not(hasItemInArray(equalTo("date:2017-01-30T00:00:00:30/01/2017"))), String[].class))
                    .andExpect(jsonPath("$.totalRows", equalTo(7456)))
                    .andReturn().getResponse().getContentAsString();


        }

        {
            String json = mockMvc.perform(delete("/api/v1/applications/hautefrequence/dataType/hautefrequence/authorization/" + authorizationId)
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(json, 50));

        }

        {
            String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .cookie(authReaderCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRows", equalTo(-1)))
                    .andReturn().getResponse().getContentAsString();
        }
    }

    @Test
    public void testAddApplicationMonsoere() throws Exception {
        fixtures.addMonsoreApplication();
    }

    @Test
    public void testAddRightForAddApplication() throws Exception {

        {
            final String TEST = "test";
            CreateUserResult dbUserResult = authenticationService.createUser(TEST, TEST);
            final Cookie dbUserCookies = mockMvc.perform(post("/api/v1/login")
                            .param("login", TEST)
                            .param("password", TEST))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
            addRoleAdmin(dbUserResult);
            String applicationCreatorLogin = "applicationCreator";
            String applicationCreatorPassword = "xxxxxxxx";
            CreateUserResult applicationCreatorResult = authenticationService.createUser(applicationCreatorLogin, applicationCreatorPassword);
            final Cookie applicationCreatorCookies = mockMvc.perform(post("/api/v1/login")
                            .param("login", applicationCreatorLogin)
                            .param("password", applicationCreatorPassword))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
            String lambdaLogin = "lambda";
            String lambdaPassword = "xxxxxxxx";
            CreateUserResult lambdaResult = authenticationService.createUser(lambdaLogin, lambdaPassword);
            final Cookie lambdaCookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", lambdaLogin)
                            .param("password", lambdaPassword))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

            {
                //l'administrateur peut créer des applications.
                fixtures.createApplicationMonSore(dbUserCookies, "monsore");
            }
            {
                // on donne les droits pour un pattern acbb

                ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                                .param("userId", applicationCreatorResult.getUserId().toString())
                                .param("applicationPattern", "acbb")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", Matchers.hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", Matchers.hasItem("acbb")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())));

                //on peut déposer acbb
                fixtures.createApplicationMonSore(applicationCreatorCookies, "acbb");

                //on ne peut déposer monsore
                try {
                    fixtures.createApplicationMonSore(applicationCreatorCookies, "monsore");
                    Assert.fail();
                } catch (NotApplicationCreatorRightsException e) {
                    Assert.assertEquals(NotApplicationCreatorRightsException.NO_RIGHT_FOR_APPLICATION_CREATION, e.getMessage());
                    Assert.assertEquals("monsore", e.applicationName);
                    Assert.assertTrue(e.applicationRestrictions.contains("acbb"));
                }
            }
            {
                //on donne des droits pour le pattern monsore
                ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                                .param("userId", applicationCreatorResult.getUserId().toString())
                                .param("applicationPattern", "monsore")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", Matchers.hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", Matchers.hasItem("monsore")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())));

                //on peut déposer monsore
                fixtures.createApplicationMonSore(applicationCreatorCookies, "monsore");
            }
            {
                //on supprime des droits pour le pattern monsore
                ResultActions resultActions = mockMvc.perform(delete("/api/v1/authorization/applicationCreator")
                                .param("userId", applicationCreatorResult.getUserId().toString())
                                .param("applicationPattern", "monsore")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", not(Matchers.hasItem("applicationCreator"))))
                        .andExpect(jsonPath("$.authorizations", not(Matchers.hasItem("monsore"))))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.getUserId().toString())));

                //on ne peut déposer monsore
                try {
                    fixtures.createApplicationMonSore(applicationCreatorCookies, "monsore");
                }catch (NotApplicationCreatorRightsException e){
                    Assert.assertEquals("monsore", e.applicationName);
                }
            }
        }

    }

    @Transactional
    void addRoleAdmin(CreateUserResult dbUserResult) {
        namedParameterJdbcTemplate.update("grant \"superadmin\" to \"" + dbUserResult.getUserId().toString() + "\"", Map.of());
    }
}