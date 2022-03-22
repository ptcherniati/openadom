package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiNg;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class RelationalServiceTest {

    @Autowired
    private RelationalService relationalService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Before
    public void createApplication() throws Exception {
        fixtures.addMonsoreApplication();
        //fixtures.addApplicationPRO();
        fixtures.addApplicationOLAC();
        fixtures.addApplicationFORET();
        fixtures.addApplicationAcbb();

    }

    @Test
    public void testCreateViews() {
//        request.setRequestClient(applicationCreatorRequestClient);
        ImmutableSet<Fixtures.Application> applications = ImmutableSet.of(Fixtures.Application.MONSORE, Fixtures.Application.ACBB, Fixtures.Application.OLAC, Fixtures.Application.FORET);
        for (Fixtures.Application application : applications) {
            String applicationName = application.getName();
            relationalService.createViews(applicationName, ViewStrategy.VIEW);
            relationalService.createViews(applicationName, ViewStrategy.TABLE);
        }

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            List<Map<String, Object>> viewContent = relationalService.readView("monsore", "pem", ViewStrategy.VIEW);
            Assert.assertEquals(306, viewContent.size());
        }

        {
            List<Map<String, Object>> viewContent = relationalService.readView("olac", "condition_prelevements", ViewStrategy.VIEW);
            Assert.assertEquals(19, viewContent.size());
        }

        {
            List<Map<String, Object>> viewContent = relationalService.readView("olac", "physico-chimie", ViewStrategy.VIEW);
            Assert.assertEquals(771, viewContent.size());
        }

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            List<Map<String, Object>> viewContent = relationalService.readView("acbb", "flux_tours", ViewStrategy.VIEW);
            Assert.assertEquals(17568, viewContent.size());
        }

        {
//            request.setRequestClient(applicationCreatorRequestClient);
            List<Map<String, Object>> viewContent = relationalService.readView("acbb", "biomasse_production_teneur", ViewStrategy.VIEW);
            Assert.assertEquals(252, viewContent.size());
        }


        {
//            request.setRequestClient(applicationCreatorRequestClient);
            List<Map<String, Object>> viewContent = relationalService.readView("acbb", "SWC", ViewStrategy.VIEW);
            Assert.assertEquals(1456, viewContent.size());
        }

        {
            // on vérifie juste le bon typage des colonnes (on ne peut moyenne que si la colonne est un nombre)
            int averageSwc = namedParameterJdbcTemplate.queryForObject("select avg(swc.\"SWC_valeur\") from acbb_view.swc where swc.\"SWC_valeur\" != -9999", Collections.emptyMap(), Integer.class);
            Assert.assertEquals(26, averageSwc);
        }

        {
            // on vérifie juste que la vue association est bien alimentée
            int numberOfRowInAssociationView = namedParameterJdbcTemplate.queryForObject("select count(*) from acbb_view.version_de_traitement_modalites natural join acbb_view.version_de_traitement join acbb_view.modalites on modalites_value = modalites_hierarchicalkey", Collections.emptyMap(), Integer.class);
            Assert.assertEquals(81, numberOfRowInAssociationView);
        }

        {
            // on vérifie juste que la vue association pour les colonnes dynamiques est bien alimentée
            // que les deux clés étrangères sont bien placées et qu'on a bien la valeur
            String sql = String.join("\n"
                    , "select count(*)"
                    , "from recursivite_view.\"taxon_propriétés de taxons\" tpt"
                    , "join recursivite_view.taxon as t on tpt.taxon_hierarchicalKey = t.taxon_hierarchicalKey"
                    , "join recursivite_view.proprietes_taxon pt on tpt.\"propriétés de taxons_hierarchicalKey\" = pt.proprietes_taxon_hierarchicalKey"
                    , "where value != ''"
            );
            int numberOfRowInAssociationView = namedParameterJdbcTemplate.queryForObject(sql, Collections.emptyMap(), Integer.class);
            Assert.assertEquals(384, numberOfRowInAssociationView);
        }

        for (Fixtures.Application application : applications) {
            String applicationName = application.getName();
            relationalService.dropViews(applicationName, ViewStrategy.VIEW);
            relationalService.dropViews(applicationName, ViewStrategy.TABLE);
        }
    }
}