package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.OreSiResourcesTest;
import fr.inra.oresing.rest.ViewStrategy;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RelationalService relationalService;


    @BeforeEach
    public void createApplication() throws Exception {
        fixtures = new Fixtures(
                mockMvc,
                userRepository,
                namedParameterJdbcTemplate,
                authenticationService
        );
        MonSoereFixture monSoereFixture = new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);
        monSoereFixture.addMonsoreApplication();
        //fixtures.addApplicationPRO();
        //fixtures.addApplicationOLAC();
        //fixtures.addApplicationFORET();
        //fixtures.addApplicationAcbb();
        fixtures.addApplicationRecursivity();

    }

    @Test
    @Tag("integration.persistence\n")
    void testCreateViews() {
//        request.setRequestClient(applicationCreatorRequestClient);
        final ImmutableSet<Fixtures.Application> applications = ImmutableSet
                .of(
                        Fixtures.Application.MONSORE,
                        //Fixtures.Application.ACBB,
                        //Fixtures.Application.OLAC,
                        //Fixtures.Application.FORET,
                        Fixtures.Application.RECURSIVITY
                );

        try {
            final String applicationsResult = mockMvc.perform(
                            asyncDispatch(
                                    mockMvc.perform(
                                                    get("/api/v1/applications?filter=DATATYPE&filter=REFERENCETYPE&filter=CONFIGURATION&filter=ADDITIONALFILE")
                                                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                                            .header("Authorization", "Bearer " + (fixtures.adminConnection.jwt()))
                                            )
                                            .andExpect(status().isOk())
                                            .andExpect(request().asyncStarted())
                                            .andReturn()
                            )
                    )
                    .andReturn().getResponse().getContentAsString();

            OreSiResourcesTest.registerFile("ui/cypress/fixtures/applications/ore/ore_application_description.txt", applicationsResult);
        } catch (final Exception e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        applications
                .forEach(application -> {
                    final String applicationName = application.getName();
                    relationalService.createViews(applicationName, ViewStrategy.VIEW);
                    relationalService.createViews(applicationName, ViewStrategy.TABLE);
                });
    }
}