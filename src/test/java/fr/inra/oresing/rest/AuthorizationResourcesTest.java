package fr.inra.oresing.rest;

import com.google.common.base.Strings;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.fixtures.AcbbFixture;
import fr.inra.oresing.rest.fixtures.HauteFrequenceFixture;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static fr.inra.oresing.rest.fixtures.MonSoereFixture.getMonsoreApplicationConfigurationResourceName;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@Slf4j
@Tag("core.auth")
public class AuthorizationResourcesTest extends AbstractIntegrationTest {

    public static final String INRAE_FR = "@inrae.fr";


    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(
                mockMvc,
                userRepository,
                namedParameterJdbcTemplate,
                authenticationService
        );
    }

    //@Test
    void testAddAuthorization() throws Exception {
        final AcbbFixture acbbFixture = new AcbbFixture(fixtures, mockMvc);
        Fixtures.CreateUser withrigths = new Fixtures.CreateUser("withrigths", "xxxxxxxx", "withrights@inrae.fr");
        final Fixtures.UserConnection withRightDefinition = fixtures.createUserForUserDefinition(withrigths, true, false);
        Fixtures.CreateUser withAdminrigths = new Fixtures.CreateUser("withAdminrigths", "xxxxxxxx", "withadminrights@inrae.fr");
        final Fixtures.UserConnection withAdminRightDefinition = fixtures.createUserForUserDefinition(withAdminrigths, true, true);
        Fixtures.CreateUser withBadRigths = new Fixtures.CreateUser("withbadadminrigths", "xxxxxxxx", "withbadadminrigths@inrae.fr");
        final Fixtures.UserConnection withBadRightDefinition = fixtures.createUserForUserDefinition(withBadRigths, true, false);
        Fixtures.CreateUser withLambdaRigths = new Fixtures.CreateUser("lambda", "xxxxxxxx", "lambda@inrae.fr");
        final Fixtures.UserConnection withLamdaRightDefinition = fixtures.createUserForUserDefinition(withLambdaRigths, true, false);
        Fixtures.CreateUser withUnReaderRigths = new Fixtures.CreateUser("UnReader", "xxxxxxxx", "UnReader@inrae.fr");
        final Fixtures.UserConnection witUnReaderRightDefinition = fixtures.createUserForUserDefinition(withUnReaderRigths, true, false);


        final String authJwt = acbbFixture.addApplicationAcbb().jwt();
        String token = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor("1234567890AZERTYUIOP000000000000".getBytes()))
                .build()
                .parseSignedClaims(authJwt)
                .getPayload()
                .getSubject();
        String authId = JsonPath.parse(token).read("$.requestclient.id");
        {
            assertEquals(1, Arrays.stream(getApplicationsFlux(authJwt, "ALL")
                    )

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "acbb".equals(JsonPath.parse(s).read("$.result.application.name", String.class)))
                    .count(), "Le créateur de l'application doit pouvoir la retrouver dans la liste");
        }

        {
            mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur/json")
                            .header("Authorization", "Bearer " + witUnReaderRightDefinition.jwt())
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().is4xxClientError());
        }

        {
            final String response = mockMvc.perform(get("/api/v1/applications/acbb/grantable")
                    .header("Authorization", "Bearer " + authJwt)
            ).andReturn().getResponse().getContentAsString();
            assertTrue(response.contains("lusignan"));
            assertTrue(response.contains("laqueuille.laqueuille__1"));
        }
        {
            // on met les droits administrateurs sur withAdminRigthsUser
            final UUID adminId = withAdminRightDefinition.userResult().userId();
            String json = "{\n" +
                          "   \"usersId\":[\"" + adminId + "\"],\n" +
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
                    .header("Authorization", "Bearer " + authJwt)
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();


            // on met les droits administrateurs sur withBadAdminRigthsUser
            json = "{\n" +
                   "   \"usersId\":[\"" + adminId + "\"],\n" +
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
                    .header("Authorization", "Bearer " + authJwt)
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();


        }

        {
            final UUID readerId = witUnReaderRightDefinition.userResult().userId();
            String json = "{\n" +
                          "   \"usersId\":[\"" + readerId + "\"],\n" +
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
                    .header("Authorization", "Bearer " + authJwt)
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            //on ajoute une autre submissionScope
            json = "{\n" +
                   "   \"usersId\":[\"" + readerId + "\",\"" + authId + "\"],\n" +
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
                    .header("Authorization", "Bearer " + authJwt)
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            // on peut aussi rajouter une submissionScope avec withAdminRigthsUserId
            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + withAdminRightDefinition.jwt())
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            // on ne peut aussi rajouter une submissionScope avec withBadAdminRigthsUserId theix vs laqueuille
            create = post("/api/v1/applications/acbb/authorization")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + withAdminRightDefinition.jwt())
                    .content(json);
            mockMvc.perform(create)
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.localizedMessage", IsEqual.equalTo("NO_RIGHT_FOR_SET_RIGHTS_APPLICATION")))
                    .andReturn().getResponse().getContentAsString();
        }
        {
            MockHttpServletRequestBuilder authorizations = get("/api/v1/applications/acbb/authorization/user/" + authId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + authJwt);
            mockMvc.perform(authorizations)
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.applicationName", equalTo("acbb")))
                    .andExpect(jsonPath("$.authorizationResults.biomasse_production_teneur.extraction[0].requiredAuthorizations.localization", equalTo("theix.theix__2")))
                    .andReturn().getResponse().getContentAsString();

        }

        {
            assertEquals(1, Arrays.stream(getApplicationsFlux(authJwt, "ALL")
                    )

                    .filter(s -> "REACTIVE_RESULT".equals(JsonPath.parse(s).read("$.type", String.class)))
                    .filter(s -> "acbb".equals(JsonPath.parse(s).read("$.result.name", String.class)))
                    .count(), "Une fois l'accès donné, on doit pouvoir avec l'application dans la liste");
        }

        {
            mockMvc.perform(get("/api/v1/applications/acbb/data/biomasse_production_teneur/json")
                            .header("Authorization", "Bearer " + witUnReaderRightDefinition.jwt())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].values.parcelle.chemin").value(hasItemInArray(equalTo("theix.theix__22")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values.localization.plateforme").value(not(hasItemInArray(equalTo("theix.theix__7"))), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(hasItemInArray(startsWith("date:2010-05-26")), String[].class))
                    .andExpect(jsonPath("$.rows[*].values['date de mesure'].valeur").value(not(hasItemInArray(startsWith("date:2010-08-31"))), String[].class))

                    .andReturn().getResponse().getContentAsString();
        }
    }

    // @Test
    public void testAddAuthorizationOnTwoScopes() throws Exception {
        HauteFrequenceFixture hauteFrequenceFixture = new HauteFrequenceFixture(fixtures, mockMvc);
        final String authJwt = hauteFrequenceFixture.addApplicationHauteFrequence().jwt();

        final CreateUserResult createUserResult = authenticationService.createUser("UnReader", "xxxxxxxx", "UnReader@inrae.fr");
        fixtures.setToActive(createUserResult.userId());
        final String readerUserId = createUserResult.userId().toString();
        final String authReaderJwt = mockMvc.perform(post("/api/v1/login")
                        .param("login", "UnReader")
                        .param("password", "xxxxxxxx"))

                .andReturn().getResponse().getHeader("Authorization");

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
                    .header("Authorization", "Bearer " + authJwt)
                    .content(json);
            final String response = mockMvc.perform(create)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            authorizationId = JsonPath.parse(response).read("$.authorizationId");
        }

        {
            final String json = mockMvc.perform(get("/api/v1/applications/hautefrequence/authorization/" + authorizationId)
                            .header("Authorization", "Bearer " + authJwt))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(json.contains("[2016,1,1]"));
        }

        {
            mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence/json")
                            .header("Authorization", "Bearer " + authReaderJwt)
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
            mockMvc.perform(delete("/api/v1/applications/hautefrequence/authorization/" + authorizationId)
                            .header("Authorization", "Bearer " + authJwt)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();

        }

        {
            Objects.requireNonNull(mockMvc.perform(get("/api/v1/applications/hautefrequence/data/hautefrequence/json")
                            .header("Authorization", "Bearer " + authReaderJwt)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string("application inconnue 'hautefrequence'"))
                    .andReturn().getResolvedException()).getMessage();
        }
    }

    @Test
    @SuppressWarnings("java:S2699") // vérifie l'absence d'exception lors du chargement du fixture
    void testAddApplicationMonsoere() throws Exception {
        MonSoereFixture monSoereFixture = new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);
        monSoereFixture.addMonsoreApplication();
    }

    @Test
    void testAddRightForAddApplication() throws Exception {
        MonSoereFixture monSoereFixture = new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);
        {
            final String TEST = "test";
            Fixtures.CreateUser testUser = new Fixtures.CreateUser(TEST, TEST, TEST + INRAE_FR);
            fixtures.createUserForUserDefinition(testUser, true, true);
            final String applicationCreatorLogin = "applicationCreator";
            final String applicationCreatorPassword = "xxxxxxxx";
            Fixtures.CreateUser applicationCreator = new Fixtures.CreateUser(applicationCreatorLogin, applicationCreatorPassword, applicationCreatorLogin + INRAE_FR);
            Fixtures.UserConnection applicationCreatorConnection = fixtures.createUserForUserDefinition(applicationCreator, true, false);


            try {
                //l'administrateur ne peut créer des applications.
                String monsoreResult = monSoereFixture.createApplicationMonSore(fixtures.adminConnection.jwt(), "monsore");
                assertFalse(Strings.isNullOrEmpty(monsoreResult));
                fail();
                monsoreResult = monSoereFixture.createApplicationMonSore(applicationCreatorConnection.jwt(), "monsore");
                assertFalse(Strings.isNullOrEmpty(monsoreResult));
                fail();
            } catch (OreSiTechnicalException e) {
                assertEquals("NO_RIGHT_FOR_APPLICATION_CREATION", e.getMessage());
            }
            {
                // on donne les droits pour un pattern acbb

                mockMvc.perform(put("/api/v1/systemrole/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorConnection.userResult().userId().toString())
                                .param("applicationPattern", "acbb")
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.user.id", IsEqual.equalTo(applicationCreatorConnection.userResult().userId().toString())))
                        .andExpect(jsonPath("$.roles.user.login", IsEqual.equalTo(applicationCreatorLogin.toLowerCase())))
                        .andExpect(jsonPath("$.roles.user.email", IsEqual.equalTo(applicationCreatorLogin.toLowerCase() + INRAE_FR)))
                        .andExpect(jsonPath("$.roles.memberOf", hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", hasItem("acbb")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorConnection.userResult().userId().toString())));

                //on peut déposer acbb
                String acbbID = monSoereFixture.createApplicationMonSore(applicationCreatorConnection.jwt(), "acbb");
                assertFalse(Strings.isNullOrEmpty(acbbID));

                try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
                    final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
                    Fixtures.getErrors(fixtures.loadApplication(configuration, applicationCreatorConnection.jwt(), "monsore", ""));
                    fail();
                } catch (NotApplicationCreatorRightsException notApplicationCreatorRightsException) {
                    assertEquals("NO_RIGHT_FOR_APPLICATION_CREATION", notApplicationCreatorRightsException.getMessage());
                    assertEquals("monsore", notApplicationCreatorRightsException.applicationName);
                } catch (Throwable e) {
                    throw new OreSiTechnicalException(e.getMessage(), e);
                }
            }
            {
                //on donne des droits pour le pattern monsore
                mockMvc.perform(put("/api/v1/systemrole/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorConnection.userResult().userId().toString())
                                .param("applicationPattern", "monsore")
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.roles.user.id", IsEqual.equalTo(applicationCreatorConnection.userResult().userId().toString())))
                        .andExpect(jsonPath("$.roles.memberOf", hasItem("applicationCreator")))
                        .andExpect(jsonPath("$.authorizations", hasItem("monsore")))
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorConnection.userResult().userId().toString())));

                //on peut déposer monsore
                String acbbId = monSoereFixture.createApplicationMonSore(applicationCreatorConnection.jwt(), "acbb");
                assertFalse(Strings.isNullOrEmpty(acbbId));

            }
            {
                //on supprime des droits pour le pattern monsore
                mockMvc.perform(delete("/api/v1/systemrole/applicationCreator")
                                .param("userIdOrLogin", applicationCreatorConnection.userResult().userId().toString())
                                .param("applicationPattern", "monsore")
                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.id", IsEqual.equalTo(applicationCreatorConnection.userResult().userId().toString())))
                        .andExpect(jsonPath("$.authorizations", not(hasItem("monsore"))))
                        .andExpect(jsonPath("$.authorizations", hasItem("acbb")));

                //on ne peut déposer monsore
                try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
                    final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
                    Fixtures.getErrors(fixtures.loadApplication(configuration, applicationCreatorConnection.jwt(), "monsore", ""));
                    fail();
                } catch (final NotApplicationCreatorRightsException notApplicationCreatorRightsException) {
                    assertEquals("NO_RIGHT_FOR_APPLICATION_CREATION", notApplicationCreatorRightsException.getMessage());
                    assertEquals("monsore", notApplicationCreatorRightsException.applicationName);
                } catch (Throwable e) {
                    throw new OreSiTechnicalException(e.getMessage(), e);
                }
            }
        }

    }


    private String[] getApplicationsFlux(final String jwt, final String... filter) throws Exception {
        return mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications")
                                .accept(MediaType.APPLICATION_NDJSON_VALUE)
                                .header("Authorization", "Bearer " + jwt)
                                .param("filter", filter))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(request().asyncStarted())
                        .andReturn()))
                .andReturn().getResponse().getContentAsString()
                .split("\n");
    }
}