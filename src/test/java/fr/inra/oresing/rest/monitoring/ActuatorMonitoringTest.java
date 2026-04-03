package fr.inra.oresing.rest.monitoring;

import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration.rest")
public class ActuatorMonitoringTest extends AbstractIntegrationTest {

    @BeforeEach
    public void init() throws Exception {
        fixtures = new Fixtures(mockMvc, userRepository, namedParameterJdbcTemplate, authenticationService);
    }

    @Test
    public void testPrometheusEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE + ";version=0.0.4;charset=utf-8"));
    }

    // Test supprimé : testCustomTagsInMetrics nécessite les containers Prometheus/Grafana lancés
    // Ce qui n'est pas possible en CI/CD
}