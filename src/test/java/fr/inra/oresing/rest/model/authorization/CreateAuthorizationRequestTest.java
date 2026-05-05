package fr.inra.oresing.rest.model.authorization;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.authorization.request.AuthorizationWithRestriction;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.authorization.request.AuthorizationRequestBuilder;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
class CreateAuthorizationRequestTest extends AbstractIntegrationTest {
    static String createAuthorization;

    @BeforeAll
    static void init() throws IOException {
        URL url = Resources.getResource("fr/inra/oresing/rest/model/authorization/createAuthorizationRequest.json");
        CreateAuthorizationRequestTest.createAuthorization = Resources.toString(url, StandardCharsets.UTF_8);
    }

    @Test
    @Tag("core.auth")
    void toAuthorizationRequest() throws IOException {
        final CreateAuthorizationRequest createAuthorizationRequest = new JsonRowMapper<CreateAuthorizationRequest>().readValue(CreateAuthorizationRequestTest.createAuthorization, CreateAuthorizationRequest.class);
        Assertions.assertEquals("e7570009-35fb-489d-ad3b-5bb335e7c5d5", createAuthorizationRequest.uuid().toString());
        Assertions.assertEquals("une submissionScope sur le référentiel monsore", createAuthorizationRequest.name());
        Assertions.assertArrayEquals(Set.of(UUID.fromString("f7570009-38fb-489d-ad3b-5bb335e7c5d5")).toArray(), createAuthorizationRequest.usersId().toArray());
        final Map<String, Set<OperationType>> authorizationForAll = createAuthorizationRequest.authorizationForAll();
        HashSet<Object> expected = new HashSet<>();
        expected.add(OperationType.extraction);

        Assertions.assertIterableEquals(
                expected,
                authorizationForAll.get("type_de_sites")
        );
        Assertions.assertIterableEquals(
                expected,
                authorizationForAll.get("sites")
        );
        final Map<String, AuthorizationInput> authorizationsWithRestriction = createAuthorizationRequest.authorizationsWithRestriction();
        AuthorizationInput pem = authorizationsWithRestriction.get("pem");
        assert pem.getOperationTypes().contains(OperationType.depot);
        Assertions.assertIterableEquals(List.of(Ltree.fromSql("projet_atlantique"), Ltree.fromSql("projet_manche")),
                pem.getRequiredAuthorizations().get("projet"));
        Assertions.assertEquals("[\"2024-03-29 00:00:00\",\"2024-03-29 00:00:00\")", pem.getTimeScope().toSqlExpression());
    }

    @Test
    @Tag("SUITE")
    void toAuthorizationRequestTest() throws IOException {
        List<AuthorizationRequestError> errors = new ArrayList<>();
        Application application = Mockito.mock(Application.class);
        UUID applicationId = UUID.fromString("41e8f1dd-4b3c-4bc7-9013-1309b4714d9d");
        UUID userId = UUID.fromString("f7570009-38fb-489d-ad3b-5bb335e7c5d5");
        Mockito.when(application.getId()).thenReturn(applicationId);
        UUID authorizationId = UUID.fromString("e7570009-35fb-489d-ad3b-5bb335e7c5d5");
        String name = "une submissionScope sur le référentiel monsore";
        List<UUID> userIds = List.of(userId);
        final CreateAuthorizationRequest createAuthorizationRequest = new JsonRowMapper<CreateAuthorizationRequest>().readValue(CreateAuthorizationRequestTest.createAuthorization, CreateAuthorizationRequest.class);
        StandardDataDescription typeDeSitesDescription = Mockito.mock(StandardDataDescription.class);
        StandardDataDescription sitesDescription = Mockito.mock(StandardDataDescription.class);
        Mockito.when(application.findData("type_de_sites")).thenReturn(Optional.of(typeDeSitesDescription));
        Mockito.when(application.findData("sites")).thenReturn(Optional.of(sitesDescription));
        OreSiAuthorization oreSiAuthorization = Mockito.mock(OreSiAuthorization.class);
        AuthorizationRequestBuilder authorizationRequestBuilder = new AuthorizationRequestBuilder(
                application,
                userIds,
                List.of(oreSiAuthorization),
                errors
        );
        AuthorizationRequest authorizationRequest = authorizationRequestBuilder.build(createAuthorizationRequest);
        Assertions.assertEquals(0, errors.size());
        Assertions.assertEquals(applicationId, authorizationRequest.applicationId());
        Assertions.assertEquals(authorizationId, authorizationRequest.authorizationId());
        Assertions.assertEquals(Set.copyOf(userIds), authorizationRequest.userId());
        Assertions.assertEquals(name, authorizationRequest.name());
        Assertions.assertEquals("""
                type_de_sites
                sites""", authorizationRequest.authorizationForAll().authorizationForAll()
                .entrySet().stream()
                .filter(entry -> entry.getValue().contains(OperationType.extraction))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("\n")));

        AuthorizationWithRestriction authorizationsWithRestriction = authorizationRequest.authorizationWithRestriction();
        Assertions.assertTrue(authorizationsWithRestriction.authorizationForScope()
                .get("pem")
                .operationTypes().contains(OperationType.depot));
        Assertions.assertEquals(
                "projet_atlantique",
                authorizationsWithRestriction.authorizationForScope().get("pem")
                        .authorizationScope()
                        .get("projet")
                        .get(0)
                        .getSql()
        );
        Assertions.assertEquals("[\"2024-03-29 00:00:00\",\"2024-03-29 00:00:00\")", authorizationsWithRestriction.authorizationForScope().get("pem").timeScope().toSqlExpression());
        Assertions.assertEquals(Ltree.fromSql("projet_manche"), authorizationsWithRestriction.authorizationForScope().get("pem").authorizationScope().get("projet").get(1));
        Mockito.when(application.findData("sites")).thenReturn(Optional.empty());
        authorizationRequest = new AuthorizationRequestBuilder(
                application,
                userIds,
                null,
                errors
        )
                .build(createAuthorizationRequest);
        String expectedJson = """
                {
                  "authorizationid" : "e7570009-35fb-489d-ad3b-5bb335e7c5d5",
                  "name" : "une submissionScope sur le référentiel monsore",
                  "description" : null,
                  "applicationid" : "41e8f1dd-4b3c-4bc7-9013-1309b4714d9d",
                  "userid" : [ "f7570009-38fb-489d-ad3b-5bb335e7c5d5" ],
                  "authorizationforall" : {
                    "authorizationforall" : {
                      "type_de_sites" : [ "extraction" ],
                      "sites" : [ "extraction" ]
                    }
                  },
                  "authorizationwithrestriction" : {
                    "authorizationforscope" : {
                      "pem" : {
                        "operationtypes" : [ "depot", "extraction" ],
                        "authorizationscope" : {
                          "projet" : [ "projet_atlantique", "projet_manche" ]
                        },
                        "timescope" : "[\\"2024-03-29 00:00:00\\",\\"2024-03-29 00:00:00\\")"
                      }
                    }
                  }
                }""";

        String actualJson = jsonRowMapper.getJsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(authorizationRequest);
        try {
            jsonRowMapper.getJsonMapper().readTree(actualJson);
            JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.LENIENT);
        } catch (JSONException e) {
            throw new OreSiTechnicalException(ExceptionMessage.JSON_EXCEPTION.toMessage(), e);
        }
    }
}