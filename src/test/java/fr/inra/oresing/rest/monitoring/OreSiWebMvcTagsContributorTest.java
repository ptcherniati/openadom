package fr.inra.oresing.rest.monitoring;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link OreSiWebMvcTagsContributor}.
 *
 * <p>Couvre : ajout tags app_name et data_type, absence de path variables,
 * context non-ServerRequestObservationContext.
 */
@Tag("domain.model")
@DisplayName("OreSiWebMvcTagsContributor — enrichissement tags Micrometer")
class OreSiWebMvcTagsContributorTest {

    private final OreSiWebMvcTagsContributor contributor = new OreSiWebMvcTagsContributor();

    private ServerRequestObservationContext contextWith(Map<String, String> pathVars) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVars);
        return new ServerRequestObservationContext(request, null);
    }

    // ─── cas normal ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("nameOrId présent → tag app_name ajouté")
    void appNameAdded() {
        ServerRequestObservationContext ctx = contextWith(Map.of("nameOrId", "myApp"));
        Observation.Context result = contributor.map(ctx);
        assertThat(result).isSameAs(ctx);
        assertThat(ctx.getLowCardinalityKeyValue("app_name"))
                .isEqualTo(KeyValue.of("app_name", "myApp"));
    }

    @Test
    @DisplayName("dataType présent → tag data_type ajouté")
    void dataTypeAdded() {
        ServerRequestObservationContext ctx = contextWith(
                Map.of("nameOrId", "myApp", "dataType", "rainfall"));
        contributor.map(ctx);
        assertThat(ctx.getLowCardinalityKeyValue("data_type"))
                .isEqualTo(KeyValue.of("data_type", "rainfall"));
    }

    @Test
    @DisplayName("dataName utilisé comme fallback quand dataType absent")
    void dataNameFallback() {
        ServerRequestObservationContext ctx = contextWith(
                Map.of("nameOrId", "myApp", "dataName", "species"));
        contributor.map(ctx);
        assertThat(ctx.getLowCardinalityKeyValue("data_type"))
                .isEqualTo(KeyValue.of("data_type", "species"));
    }

    @Test
    @DisplayName("ni dataType ni dataName → pas de tag data_type")
    void noDataType() {
        ServerRequestObservationContext ctx = contextWith(Map.of("nameOrId", "myApp"));
        contributor.map(ctx);
        // data_type ne doit pas être présent
        assertThat(ctx.getLowCardinalityKeyValues())
                .noneMatch(kv -> kv.getKey().equals("data_type"));
    }

    @Test
    @DisplayName("pathVariables absent (null) → aucun tag ajouté")
    void noPathVariables() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // pas d'attribut URI_TEMPLATE_VARIABLES_ATTRIBUTE
        ServerRequestObservationContext ctx = new ServerRequestObservationContext(request, null);
        contributor.map(ctx);
        assertThat(ctx.getLowCardinalityKeyValues()).isEmpty();
    }

    @Test
    @DisplayName("context non-ServerRequestObservationContext → retourné tel quel")
    void nonServerContext() {
        Observation.Context other = new Observation.Context();
        Observation.Context result = contributor.map(other);
        assertThat(result).isSameAs(other);
        // pas d'exception
    }

    @Test
    @DisplayName("dataType prioritaire sur dataName quand les deux sont présents")
    void dataTypePriorityOverDataName() {
        ServerRequestObservationContext ctx = contextWith(
                Map.of("nameOrId", "a", "dataType", "ref", "dataName", "data"));
        contributor.map(ctx);
        assertThat(ctx.getLowCardinalityKeyValue("data_type"))
                .isEqualTo(KeyValue.of("data_type", "ref"));
    }
}
