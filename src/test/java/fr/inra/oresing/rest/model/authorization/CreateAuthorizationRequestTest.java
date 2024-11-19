package fr.inra.oresing.rest.model.authorization;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.authorization.request.AuthorizationWithRestriction;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.rest.AuthorizationService;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.authorization.request.AuthorizationRequestBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.mockito.internal.matchers.Any;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

class CreateAuthorizationRequestTest {
    static String createAuthorization;

    @BeforeAll
    static void init() throws IOException {
        URL url = Resources.getResource("fr/inra/oresing/rest/model/authorization/createAuthorizationRequest.json");
        CreateAuthorizationRequestTest.createAuthorization = Resources.toString(url, StandardCharsets.UTF_8);
    }

    @Test
    @Tag("SUITE")
    void toAuthorizationRequest() throws IOException {
        CreateAuthorizationRequest createAuthorizationRequest1 = new CreateAuthorizationRequest(
                UUID.fromString("e7570009-35fb-489d-ad3b-5bb335e7c5d5"),
                "une submissionScope sur le référentiel monsore",
                "une description",
                Set.of(UUID.fromString("f7570009-38fb-489d-ad3b-5bb335e7c5d5")),
                Map.of(
                        "type_de_sites", Set.of(OperationType.extraction),
                        "sites", Set.of(OperationType.extraction)
                ),
                Map.of(
                        "pem", new AuthorizationInput(
                                Map.of("projet", List.of(Ltree.fromSql("projet_atlantique"), Ltree.fromSql("projet_manche"))),
                                LocalDateTimeRange.between(
                                        LocalDate.of(2024, 3, 29),
                                        LocalDate.of(2024, 3, 29)
                                ),
                                Set.of(OperationType.depot)
                        )
                )
        );
        final CreateAuthorizationRequest createAuthorizationRequest = new JsonRowMapper<CreateAuthorizationRequest>().readValue(CreateAuthorizationRequestTest.createAuthorization, CreateAuthorizationRequest.class);
        Assert.assertEquals("e7570009-35fb-489d-ad3b-5bb335e7c5d5", createAuthorizationRequest.uuid().toString());
        Assert.assertEquals("une submissionScope sur le référentiel monsore", createAuthorizationRequest.name());
        Assert.assertEquals(Set.of(UUID.fromString("f7570009-38fb-489d-ad3b-5bb335e7c5d5")).toArray(), createAuthorizationRequest.usersId().toArray());
        final Map<String, Set<OperationType>> authorizationForAll = createAuthorizationRequest.authorizationForAll();
        Assertions.assertIterableEquals(
                new HashSet<>() {{
                    this.add(OperationType.extraction);
                }},
                authorizationForAll.get("type_de_sites")
        );
        Assertions.assertIterableEquals(
                new HashSet<>() {{
                    this.add(OperationType.extraction);
                }},
                authorizationForAll.get("sites")
        );
        final Map<String, AuthorizationInput> authorizationsWithRestriction = createAuthorizationRequest.authorizationsWithRestriction();
        AuthorizationInput pem = authorizationsWithRestriction.get("pem");
        assert pem.getOperationTypes().contains(OperationType.depot);
        Assertions.assertIterableEquals(List.of(Ltree.fromSql("projet_atlantique"), Ltree.fromSql("projet_manche")),
                pem.getRequiredAuthorizations().get("projet"));
        Assert.assertEquals("[\"2024-03-29 00:00:00\",\"2024-03-29 00:00:00\")", pem.getTimeScope().toSqlExpression());
    }

    @Test
    @Tag("SUITE")
    @Disabled
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
        DataRepositoryWithBuffer dataRepositoryWithBuffer = Mockito.mock(DataRepositoryWithBuffer.class);
        OreSiAuthorization oreSiAuthorization = Mockito.mock(OreSiAuthorization.class);
        Mockito.doReturn(
                List.of(
                        Ltree.fromSql("projetKprojet_atlantique"),
                        Ltree.fromSql("projetKprojet_manche")
                )
        ).when(dataRepositoryWithBuffer).getHierarchicalKeyForEntry(Mockito.any(Map.Entry.class));
        Mockito.doReturn(
                List.of(
                        Ltree.fromSql("projetKprojet_atlantique"),
                        Ltree.fromSql("projetKprojet_manche")
                )
        ).when(dataRepositoryWithBuffer).getHierarchicalKeyForEntry(Mockito.any(Map.Entry.class));
        AuthorizationRequestBuilder authorizationRequestBuilder = new AuthorizationRequestBuilder(
                application,
                userIds,
                List.of(oreSiAuthorization),
                errors
        );
        AuthorizationRequest authorizationRequest = authorizationRequestBuilder.build(createAuthorizationRequest, dataRepositoryWithBuffer);
        Assert.assertEquals(0, errors.size());
        Assert.assertEquals(applicationId, authorizationRequest.applicationId());
        Assert.assertEquals(authorizationId, authorizationRequest.authorizationId());
        Assert.assertEquals(Set.copyOf(userIds), authorizationRequest.userId());
        Assert.assertEquals(name, authorizationRequest.name());
        Assert.assertEquals(
                new ArrayList<String>() {{
                    this.add("type_de_sites");
                    this.add("sites");
                }},
                authorizationRequest.authorizationForAll().authorizationForAll().get(OperationType.extraction)
        );
        AuthorizationWithRestriction authorizationsWithRestriction = authorizationRequest.authorizationWithRestriction();
        final AuthorizationForScope authorization1 = null /*authorizationsWithRestriction.authorizationForScope().get("pem").get(OperationType.depot).get(0)*/;
        Assert.assertEquals(null, authorization1.timeScope());
        Assert.assertEquals(Ltree.fromSql("projet_atlantique"), authorization1.authorizationScope().get("projet"));
        final AuthorizationForScope authorization2 = null /*authorizationsWithRestriction.authorizationForScope().get("pem").get(OperationType.depot).get(1)*/;
        Assert.assertEquals("[\"2024-03-29 00:00:00\",\"2024-03-29 00:00:00\")", authorization2.timeScope().toSqlExpression());
        Assert.assertEquals(Ltree.fromSql("projet_manche"), authorization2.authorizationScope().get("projet"));
        Mockito.when(application.findData("sites")).thenReturn(Optional.empty());
        /*authorizationRequest = createAuthorizationRequest.toAuthorizationRequest(
                application,
                userIds,
                null,
                errors
        )*/
        ;
        Assert.assertEquals("""
                        {
                          "error" : "badReferences",
                          "params" : {
                            "badReferences" : [ "sites" ]
                          }
                        }""",
                errors.get(0).getAuthorizationRequestString());
    }
}