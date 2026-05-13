package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.JsonRowMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link NormalizedService#buildNormalizedSchema(Application, boolean)}
 * avec {@code execute=false} : aucune base de données ni contexte Spring requis.
 *
 * <p>Les dépendances Spring (repository, beanFactory, sqlService) ne sont
 * jamais invoquées quand {@code execute=false}, donc on passe {@code null}.
 */
@Tag("domain.model")
@DisplayName("NormalizedService — buildNormalizedSchema (execute=false)")
class NormalizedServiceDomainModelTest {

    private final JsonRowMapper<Application> mapper = new JsonRowMapper<>();
    private final NormalizedService service = new NormalizedService(null, null, null, null);

    @Test
    @DisplayName("MONSOERE : génère un schéma SQL non vide")
    void buildNormalizedSchema_monsore() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/data/monsore/normalized/monsoereApplication.json")) {
            assertThat(stream).isNotNull();
            Application app = mapper.readStream(stream, Application.class);
            String sql = service.buildNormalizedSchema(app, false);
            assertThat(sql).isNotNull().isNotEmpty();
        }
    }

    @Test
    @DisplayName("Multiplicity : génère un schéma SQL non vide")
    void buildNormalizedSchema_multiplicity() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/data/multiplicity/normalized/multiplicityApplication.json")) {
            assertThat(stream).isNotNull();
            Application app = mapper.readStream(stream, Application.class);
            String sql = service.buildNormalizedSchema(app, false);
            assertThat(sql).isNotNull().isNotEmpty();
        }
    }

    @Test
    @DisplayName("Pattern : génère un schéma SQL non vide")
    void buildNormalizedSchema_pattern() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/data/pattern/normalized/patternApplication.json")) {
            assertThat(stream).isNotNull();
            Application app = mapper.readStream(stream, Application.class);
            String sql = service.buildNormalizedSchema(app, false);
            assertThat(sql).isNotNull().isNotEmpty();
        }
    }
}
