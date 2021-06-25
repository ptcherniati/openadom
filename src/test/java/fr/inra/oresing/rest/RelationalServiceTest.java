package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

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
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private OreSiApiRequestContext request;

    private OreSiUserRequestClient applicationCreatorRequestClient;

    private OreSiRequestClient restrictedReaderRequestClient;

    private String excludedReferenceId;

    @Before
    public void createApplication() throws Exception {
        fixtures.addMonsoreApplication();
        fixtures.addApplicationPRO();
        fixtures.addApplicationAcbb();
    }

    @Test
    public void testCreateViews() {
//        request.setRequestClient(applicationCreatorRequestClient);
        ImmutableSet<Fixtures.Application> applications = ImmutableSet.of(Fixtures.Application.MONSORE, Fixtures.Application.PRO, Fixtures.Application.ACBB);
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
            List<Map<String, Object>> viewContent = relationalService.readView("pros", "donnees_prelevement_pro", ViewStrategy.VIEW);
            Assert.assertEquals(80, viewContent.size());
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

        for (Fixtures.Application application : applications) {
            String applicationName = application.getName();
            relationalService.dropViews(applicationName, ViewStrategy.VIEW);
            relationalService.dropViews(applicationName, ViewStrategy.TABLE);
        }
    }
}