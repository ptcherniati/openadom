package fr.inra.oresing.rest;

import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.google.common.base.Strings;
import org.hamcrest.core.IsEqual;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

import java.io.InputStream;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
@Tag("core.auth")
public class AuthorizationResourcesTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SqlService db;


    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;


    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private Fixtures fixtures;

    @Test
    @Disabled
    public void testAddAuthorization() throws Exception {
        final CreateUserResult withRightsUserResult = authenticationService.createUser("withrigths", "xxxxxxxx", "withrights@inrae.fr");
        fixtures.setToActive(withRightsUserResult.userId());
        final String withRigthsUserId = withRightsUserResult.userId().toString();
        final Cookie withRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult withAdminRightsUserResult = authenticationService.createUser("withadminrigths", "xxxxxxxx", "withadminrights@inrae.fr");
        fixtures.setToActive(withAdminRightsUserResult.userId());
        final String withAdminRigthsUserId = withAdminRightsUserResult.userId().toString();
        final Cookie withAdminRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withadminrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult withBadAdminRightsUserResult = authenticationService.createUser("withbadadminrigths", "xxxxxxxx", "withbadadminrigths@inrae.fr");
        fixtures.setToActive(withBadAdminRightsUserResult.userId());
        final String withBadAdminRigthsUserId = withBadAdminRightsUserResult.userId().toString();
        final Cookie withBadAdminRigthsCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "withbadadminrigths")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult lamdaUserResult = authenticationService.createUser("lambda", "xxxxxxxx", "lambda@inrae.fr");
        fixtures.setToActive(lamdaUserResult.userId());
        final String lambdaUserId = lamdaUserResult.userId().toString();
        final Cookie lambdaCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "lambda")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        final CreateUserResult readerUserResult = authenticationService.createUser("UnReader", "xxxxxxxx", "UnReader@inrae.fr");
        fixtures.setToActive(readerUserResult.userId());
        final Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "UnReader")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        final String readerUserId = readerUserResult.userId().toString();

        final Cookie authCookie = fixtures.addApplicationAcbb(null);
        String token = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor("1234567890AZERTYUIOP000000000000".getBytes()))
                .build()
                .parseSignedClaims(authCookie.getValue())
                .getPayload()
                .getSubject();
        String authId = JsonPath.parse(token).read("$.requestClient.id");
        {
            assertEquals(1, Arrays.stream(getApplicationsFlux(authCookie, "ALL")
                    )

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "acbb".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count(), "Le créateur de l'application doit pouvoir la retrouver dans la liste");
        }

        {
            assertEquals(1, Arrays.stream(getApplicationsFlux(authCookie, "ALL")
                    )

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "acbb".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count(), "Le créateur de l'application doit pouvoir la retrouver dans la liste");
        }

        {
            mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur/json")
                            .cookie(authReaderCookie)
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
            final String response = mockMvc.perform(get("/api/v1/applications/acbb/grantable")
                    .cookie(authCookie)
            ).andReturn().getResponse().getContentAsString();
            assertTrue(response.contains("lusignan"));
            assertTrue(response.contains("laqueuille.laqueuille__1"));
        }
        {
            // on met les droits administrateurs sur withAdminRigthsUser
            String json = "{\n" +
                          "   \"usersId\":[\"" + withAdminRigthsUserId + "\"],\n" +
                          "   \"applicationNameOrId\":\"acbb\",\n" +
                          "   \"id\": null,\n" +
                          "   \"name\": \"une submissionScope sur acbb\",\n" +
                          "   \"authorizations\":{\n" +
                          "   \"biomasse_production_teneur\":{\n" +
                          "   \"admin\":[\n" +
                          "      {\n" +
                          "         \"requiredAuthorizations\":{\n" +
                          "            \"localization\":\"theix\"\n" +
                          "         }\n" +
                          "      }\n" +
                          "   ]\n" +
                          "  }\n" +
                          " }\n" +
                          "}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();


            // on met les droits administrateurs sur withBadAdminRigthsUser
            json = "{\n" +
                   "   \"usersId\":[\"" + withAdminRigthsUserId + "\"],\n" +
                   "   \"applicationNameOrId\":\"acbb\",\n" +
                   "   \"id\": null,\n" +
                   "   \"name\": \"une submissionScope sur acbb\",\n" +
                   "   \"authorizations\":{\n" +
                   "   \"biomasse_production_teneur\":{\n" +
                   "   \"admin\":[\n" +
                   "      {\n" +
                   "         \"requiredAuthorizations\":{\n" +
                   "            \"localization\":\"laqueuille\"\n" +
                   "         }\n" +
                   "      }\n" +
                   "   ]\n" +
                   "  }\n" +
                   " }\n" +
                   "}";

            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();


        }

        {
            String json = "{\n" +
                          "   \"usersId\":[\"" + readerUserId + "\"],\n" +
                          "   \"applicationNameOrId\":\"acbb\",\n" +
                          "   \"id\": null,\n" +
                          "   \"name\": \"une submissionScope sur acbb\",\n" +
                          "   \"dataName\":\"biomasse_production_teneur\",\n" +
                          "   \"authorizations\":{\n" +
                          "   \"biomasse_production_teneur\":{\n" +
                          "   \"extraction\":[\n" +
                          "      {\n" +
                          "         \"requiredAuthorizations\":{\n" +
                          "            \"localization\":\"theix.theix__22\"\n" +
                          "         },\n" +
                          "         \"datagroups\":[\n" +
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
                          "  }\n" +
                          " }\n" +
                          "}";

            MockHttpServletRequestBuilder create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();


            log.debug(StringUtils.abbreviate(response, 50));
            //on ajoute une autre submissionScope
            json = "{\n" +
                   "   \"usersId\":[\"" + readerUserId + "\",\"" + authId + "\"],\n" +
                   "   \"applicationNameOrId\":\"acbb\",\n" +
                   "   \"id\": null,\n" +
                   "   \"name\": \"une autre submissionScope sur acbb\",\n" +
                   "   \"dataName\":\"biomasse_production_teneur\",\n" +
                   "   \"authorizations\":{\n" +
                   "   \"biomasse_production_teneur\":{\n" +
                   "   \"extraction\":[\n" +
                   "      {\n" +
                   "         \"requiredAuthorizations\":{\n" +
                   "            \"localization\":\"theix.theix__2\"\n" +
                   "         },\n" +
                   "         \"dataGroups\":[\n" +
                   "            \"all\"\n" +
                   "         ],\n" +
                   "         \"intervalDates\":{\n" +
                   "            \"fromDay\":[\n" +
                   "               2009,\n" +
                   "               1,\n" +
                   "               1\n" +
                   "            ],\n" +
                   "            \"toDay\":[\n" +
                   "               2009,\n" +
                   "               6,\n" +
                   "               1\n" +
                   "            ]\n" +
                   "         }\n" +
                   "      }\n" +
                   "   ]\n" +
                   "  }\n" +
                   " }\n" +
                   "}";
            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
            // on peut aussi rajouter une submissionScope avec withAdminRigthsUserId
            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(withAdminRigthsCookie)
                    .content(json);
            response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
            // on ne peut aussi rajouter une submissionScope avec withBadAdminRigthsUserId theix vs laqueuille
            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(withBadAdminRigthsCookie)
                    .content(json);
            response = mockMvc.perform(create)
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.localizedMessage", IsEqual.equalTo("NO_RIGHT_FOR_SET_RIGHTS_APPLICATION")))
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));
        }
        {
            MockHttpServletRequestBuilder authorizations = get("/api/v1/applications/acbb/authorization/user/" + authId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie);
            mockMvc.perform(authorizations)
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.applicationName", equalTo("acbb")))
                    .andExpect(jsonPath("$.authorizationResults.biomasse_production_teneur.extraction[0].requiredAuthorizations.localization", equalTo("theix.theix__2")))
                    .andReturn().getResponse().getContentAsString();

        }

        {
            assertEquals(1, Arrays.stream(getApplicationsFlux(authCookie, "ALL")
                    )

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "acbb".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count(), "Une fois l'accès donné, on doit pouvoir avec l'application dans la liste");
        }

        {
            final String json = mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur/json")
                            .cookie(authReaderCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.parcelle.chemin").value(hasItemInArray(equalTo("theix.theix__22")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(not(hasItemInArray(equalTo("theix.theix__7"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(hasItemInArray(startsWith("date:2010-05-26")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(not(hasItemInArray(startsWith("date:2010-08-31"))), String[].class))

                    .andReturn().getResponse().getContentAsString();
        }
    }

    @Test
    @Disabled
    public void testAddAuthorizationOnTwoScopes() throws Exception {
        final Cookie authCookie = fixtures.addApplicationHauteFrequence();

        final CreateUserResult createUserResult = authenticationService.createUser("UnReader", "xxxxxxxx", "UnReader@inrae.fr");
        fixtures.setToActive(createUserResult.userId());
        final String readerUserId = createUserResult.userId().toString();
        final Cookie authReaderCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "UnReader")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

        final String authorizationId;

        {

            final String json = "{\n" +
                                "   \"usersId\":[\"" + readerUserId + "\"],\n" +
                                "   \"applicationNameOrId\":\"hautefrequence\",\n" +
                                "   \"id\": null,\n" +
                                "   \"name\": \"une submissionScope sur haute fréquence\",\n" +
                                "   \"authorizations\":{\n" +
                                "   \"hautefrequence\":{\n" +
                                "   \"extraction\":[\n" +
                                "      {\n" +
                                "         \"requiredAuthorizations\":{\n" +
                                "            \"localization\":\"bimont.bim13\",\n" +
                                "            \"projet\":\"sou\"\n" +
                                "         },\n" +
                                "         \"datagroups\":[\n" +
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
                                "  }\n" +
                                " }\n" +
                                "}";

            final MockHttpServletRequestBuilder create = post("/api/v1/applications/hautefrequence/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .cookie(authCookie)
                    .content(json);
            final String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            log.debug(StringUtils.abbreviate(response, 50));

            authorizationId = JsonPath.parse(response).read("$.authorizationId");
        }

        {
            final String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/authorization/" + authorizationId)
                            .cookie(authCookie))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(json, 50));

            assertTrue(json.contains("[2016,1,1]"));
        }

        {
            final String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence/json")
                            .cookie(authReaderCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(hasItemInArray(equalTo("bimont.bim13")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(not(hasItemInArray(equalTo("bimont.bim14"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.projet").value(hasItemInArray(equalTo("sou")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.projet").value(not(hasItemInArray(equalTo("rnt"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.date.day").value(hasItemInArray(equalTo("date:2016-06-14T00:00:00:dd/MM/yyyy")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.date.day").value(not(hasItemInArray(equalTo("date:2017-01-30T00:00:00:dd/MM/yyyy"))), String[].class))
                    .andExpect(jsonPath("$.totalRows", equalTo(7456)))
                    .andReturn().getResponse().getContentAsString();


        }

        {
            final String json = mockMvc.perform(delete("/api/v1/applications/hautefrequence/authorization/" + authorizationId)
                            .cookie(authCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

            log.debug(StringUtils.abbreviate(json, 50));

        }

        {
            final String json = Objects.requireNonNull(mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence/json")
                            .cookie(authReaderCookie)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'hautefrequence'"))
                    .andReturn().getResolvedException()).getMessage();
        }
    }

    @Test
    @Disabled
    public void testAddApplicationMonsoere() throws Exception {
        fixtures.addMonsoreApplication();
    }

    @Test
    @Disabled
    public void testAddRightForAddApplication() throws Exception {

        {
            final String TEST = "test";
            final CreateUserResult dbUserResult = authenticationService.createUser(TEST, TEST, TEST + "@inrae.fr");
            fixtures.setToActive(dbUserResult.userId());
            Cookie dbUserCookies = mockMvc.perform(post("/api/v1/login")
                            .param("login", TEST)
                            .param("password", TEST))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
            addRoleAdmin(dbUserResult);
            final String applicationCreatorLogin = "applicationCreator";
            final String applicationCreatorPassword = "xxxxxxxx";
            final CreateUserResult applicationCreatorResult = authenticationService.createUser(applicationCreatorLogin, applicationCreatorPassword, applicationCreatorLogin + "@inrae.fr");
            fixtures.setToActive(applicationCreatorResult.userId());
            Cookie applicationCreatorCookies = mockMvc.perform(post("/api/v1/login")
                            .param("login", applicationCreatorLogin)
                            .param("password", applicationCreatorPassword))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
            final String lambdaLogin = "lambda";
            final String lambdaPassword = "xxxxxxxx";
            final CreateUserResult lambdaResult = authenticationService.createUser(lambdaLogin, lambdaPassword, "lambdaLogin@inrae.fr");
            fixtures.setToActive(lambdaResult.userId());
            Cookie lambdaCookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", lambdaLogin)
                            .param("password", lambdaPassword))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);

            {
                //l'administrateur peut créer des applications.
                String monsoreResult = fixtures.createApplicationMonSore(dbUserCookies, "monsore");
                assertFalse(Strings.isNullOrEmpty(monsoreResult));
            }
            {
                // on donne les droits pour un pattern acbb

                final ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorResult.userId().toString())
                                .param("applicationPattern", "acbb")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.userId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", hasItem("acbb")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.userId().toString())));

                //on peut déposer acbb
                String acbbID = fixtures.createApplicationMonSore(applicationCreatorCookies, "acbb");
                assertFalse(Strings.isNullOrEmpty(acbbID));

                try (final InputStream configurationFile = getClass().getResourceAsStream(Fixtures.getMonsoreApplicationConfigurationResourceName())) {
                    final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
                    final List<ReactiveTypeError> errors = Fixtures.getErrors(fixtures.loadApplication(configuration, applicationCreatorCookies, "monsore", ""));
                    Map validationCheckResult = (((LinkedHashMap) errors.getFirst().result()));
                    fail();
                } catch (NotApplicationCreatorRightsException notApplicationCreatorRightsException) {
                    assertEquals("NO_RIGHT_FOR_APPLICATION_CREATION", notApplicationCreatorRightsException.getMessage());
                    assertEquals("monsore", notApplicationCreatorRightsException.applicationName);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            {
                //on donne des droits pour le pattern monsore
                final ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorResult.userId().toString())
                                .param("applicationPattern", "monsore")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.userId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", hasItem("monsore")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.userId().toString())));

                //on peut déposer monsore
                String acbbId = fixtures.createApplicationMonSore(applicationCreatorCookies, "acbb");
                assertFalse(Strings.isNullOrEmpty(acbbId));

            }
            {
                //on supprime des droits pour le pattern monsore
                final ResultActions resultActions = mockMvc.perform(delete("/api/v1/authorization/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorResult.userId().toString())
                                .param("applicationPattern", "monsore")
                                .cookie(dbUserCookies))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(applicationCreatorResult.userId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", not(hasItem("applicationCreator"))))
                        .andExpect(jsonPath("$.authorizations", not(hasItem("monsore"))))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorResult.userId().toString())));

                //on ne peut déposer monsore
                try (final InputStream configurationFile = getClass().getResourceAsStream(Fixtures.getMonsoreApplicationConfigurationResourceName())) {
                    final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
                    final List<ReactiveTypeError> errors = Fixtures.getErrors(fixtures.loadApplication(configuration, applicationCreatorCookies, "monsore", ""));
                    fail();
                } catch (final NotApplicationCreatorRightsException notApplicationCreatorRightsException) {
                    assertEquals("NO_RIGHT_FOR_APPLICATION_CREATION", notApplicationCreatorRightsException.getMessage());
                    assertEquals("monsore", notApplicationCreatorRightsException.applicationName);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    @Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        String sql = """
                GRANT openadomadmin TO :userid WITH INHERIT TRUE
                """;

        namedParameterJdbcTemplate.update(
                sql,
                Map.of("userId", dbUserResult.userId().toString())
        );
    }


    private String[] getApplicationsFlux(final Cookie cookie, final String... filter) throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications")
                                .accept(MediaType.APPLICATION_NDJSON_VALUE)
                                .cookie(cookie)
                                .param("filter", filter))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(request().asyncStarted())
                        .andReturn()))
                .andReturn().getResponse().getContentAsString()
                .split("\n");
    }
}
