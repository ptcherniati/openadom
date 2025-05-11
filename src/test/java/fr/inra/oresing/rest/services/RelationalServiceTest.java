package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.TestDatabaseConfig;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.OreSiResourcesTest;
import fr.inra.oresing.rest.ViewStrategy;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
public class RelationalServiceTest {

    @Autowired
    private RelationalService relationalService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private MockMvc mockMvc;


    @BeforeEach
    public void createApplication() throws Exception {
        fixtures.addMonsoreApplication();
        //fixtures.addApplicationPRO();
        fixtures.addApplicationOLAC();
        fixtures.addApplicationFORET();
        fixtures.addApplicationAcbb(null);
        fixtures.addApplicationRecursivity();

    }

    @Test
    @Disabled
    @Tag("integration.persistence\n")
    public void testCreateViews() {
//        request.setRequestClient(applicationCreatorRequestClient);
        final ImmutableSet<Fixtures.Application> applications = ImmutableSet
                .of(
                        Fixtures.Application.MONSORE,
                        Fixtures.Application.ACBB,
                        Fixtures.Application.OLAC,
                        Fixtures.Application.FORET,
                        Fixtures.Application.RECURSIVITY
                );

        try {
            final String applicationsResult = mockMvc.perform(
                            asyncDispatch(
                                    mockMvc.perform(
                                                    get("/api/v1/applications?filter=DATATYPE&filter=REFERENCETYPE&filter=CONFIGURATION&filter=ADDITIONALFILE")
                                                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                                            .cookie(fixtures.addopenAdomAdmin(".*")))
                                            .andExpect(status().isOk())
                                            .andExpect(request().asyncStarted())
                                            .andReturn()
                            )
                    )
                    .andReturn().getResponse().getContentAsString();

            OreSiResourcesTest.registerFile("ui/cypress/fixtures/applications/ore/ore_application_description.txt", applicationsResult);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        applications
                .forEach(application -> {
                    final String applicationName = application.getName();
                    relationalService.createViews(applicationName, ViewStrategy.VIEW);
                    relationalService.createViews(applicationName, ViewStrategy.TABLE);
                });

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            final List<Map<String, Object>> viewContent = relationalService.readView("monsore", "pem", ViewStrategy.VIEW);
            assertEquals(272, viewContent.size());
        }

        {
            final List<Map<String, Object>> viewContent = relationalService.readView("olac", "condition_prelevements", ViewStrategy.VIEW);
            assertEquals(2169, viewContent.size());
        }

        {
            final List<Map<String, Object>> viewContent = relationalService.readView("olac", "physico-chimie", ViewStrategy.VIEW);
            assertEquals(2169, viewContent.size());
        }

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            final List<Map<String, Object>> viewContent = relationalService.readView("acbb", "flux_tours", ViewStrategy.VIEW);
            assertEquals(19276, viewContent.size());
        }

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            final List<Map<String, Object>> viewContent = relationalService.readView("acbb", "biomasse_production_teneur", ViewStrategy.VIEW);
            assertEquals(19276, viewContent.size());
        }


        {
//            request.setRequestClient(applicationCreatorRequestClient);
            final List<Map<String, Object>> viewContent = relationalService.readView("acbb", "SWC", ViewStrategy.VIEW);
            assertEquals(19276, viewContent.size());
        }

        {
            // on vérifie juste le bon typage des colonnes (on ne peut moyenne que si la colonne est un nombre)
            final int averageSwc = namedParameterJdbcTemplate.queryForObject("select avg(swc.\"swc_valeur\") from acbb_view.swc where swc.\"swc_valeur\" != -9999", Collections.emptyMap(), Integer.class);
            assertEquals(26, averageSwc);
        }

        {
            // on vérifie juste que la vue association est bien alimentée
            final int numberOfRowInAssociationView = namedParameterJdbcTemplate.queryForObject("""
                    select count(*)
                    from acbb_view.version_de_traitement_modalites
                    natural join acbb_view.version_de_traitement
                    join acbb_view.modalites on modalites_value::text = modalites.modalites_hierachicakkey::text""", Collections.emptyMap(), Integer.class);
            assertEquals(81, numberOfRowInAssociationView);
        }

        {
            // on vérifie juste que la vue association pour les colonnes dynamiques est bien alimentée
            // que les deux clés étrangères sont bien placées et qu'on a bien la valeur
            final String sql = "select count(*) from recursivite_view.\"taxon_propriétés de taxons\" tpt " +
                    "join recursivite_view.taxon as t on tpt.taxon_hierachicakkey = t.taxon_hierachicakkey " +
                    "join recursivite_view.proprietes_taxon pt on tpt.\"_1propriétés de taxons_hierachicakKey\" = pt.proprietes_taxon_hierachicakkey " +
                    "where value != '';";
            final int numberOfRowInAssociationView = namedParameterJdbcTemplate.queryForObject(sql, Collections.emptyMap(), Integer.class);
            assertEquals(424, numberOfRowInAssociationView);
        }

        applications
                .forEach(application -> {
                    final String applicationName = application.getName();
                    relationalService.dropViews(applicationName, ViewStrategy.VIEW);
                    relationalService.dropViews(applicationName, ViewStrategy.TABLE);
                });
    }
}