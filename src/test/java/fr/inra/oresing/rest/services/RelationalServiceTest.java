package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.OreSiResourcesTest;
import fr.inra.oresing.rest.ViewStrategy;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class, TestDatabaseConfig.class})

@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RelationalServiceTest {

    @Autowired
    private RelationalService relationalService;

    private Fixtures fixtures;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonRowMapper jsonRowMapper;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private UserRepository userRepository;


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
                                                            .cookie(fixtures.adminConnection.cookie()))
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