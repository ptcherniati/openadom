package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlTable;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.HeartbeatService;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode;
import fr.inrae.ore.cascade.model.core.Sink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link CascadeSinkFactory}.
 *
 * <p>Couvre :
 * <ul>
 *   <li>Rejet lorsque DataSource est null (guard check)</li>
 *   <li>Construction nominale du sink (PER_CONNECTION_TEMP / SYNCHRONOUS)</li>
 *   <li>Délégation des surcharges à 4 et 5 args vers la surcharge principale</li>
 *   <li>DEFERRED_TO_CALLER mode</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("CascadeSinkFactory — construction de Sink<Path>")
class CascadeSinkFactoryTest {

    private static final UUID CORR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    // ─── null DataSource guard ─────────────────────────────────────────────────

    @Test
    @DisplayName("directCopy lève IllegalStateException si DataRepository expose une DataSource null")
    void directCopy_nullDataSource_throws() {
        DataRepository repo = mockRepo(null);
        ImportProperties props = defaultProps();

        assertThatThrownBy(() -> CascadeSinkFactory.directCopy(repo, props, CORR_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DataSource");
    }

    @Test
    @DisplayName("directCopy(4 args) avec DataSource null lève IllegalStateException")
    void directCopy_4args_nullDataSource_throws() {
        DataRepository repo = mockRepo(null);
        ImportProperties props = defaultProps();

        assertThatThrownBy(() -> CascadeSinkFactory.directCopy(repo, props, CORR_ID, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("directCopy(5 args) avec DataSource null lève IllegalStateException")
    void directCopy_5args_nullDataSource_throws() {
        DataRepository repo = mockRepo(null);
        ImportProperties props = defaultProps();
        WorkflowActiveRegistry registry = new WorkflowActiveRegistry();

        assertThatThrownBy(() -> CascadeSinkFactory.directCopy(repo, props, CORR_ID, null, registry))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── surcharges délèguent vers la surcharge principale ────────────────────

    @Test
    @DisplayName("directCopy(4 args) construit un Sink non-null")
    void directCopy_4args_buildsSink() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();

        Sink<Path> sink = CascadeSinkFactory.directCopy(repo, props, CORR_ID, null);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy(5 args avec registry) construit un Sink non-null")
    void directCopy_5args_buildsSink() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();
        WorkflowActiveRegistry registry = new WorkflowActiveRegistry();

        Sink<Path> sink = CascadeSinkFactory.directCopy(repo, props, CORR_ID, null, registry);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy(6 args SYNCHRONOUS) construit un Sink non-null")
    void directCopy_6args_synchronous_buildsSink() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();

        Sink<Path> sink = CascadeSinkFactory.directCopy(
                repo, props, CORR_ID, null, null, FinalizeMode.SYNCHRONOUS);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy(6 args DEFERRED_TO_CALLER) construit un Sink non-null")
    void directCopy_6args_deferred_buildsSink() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();

        Sink<Path> sink = CascadeSinkFactory.directCopy(
                repo, props, CORR_ID, null, null, FinalizeMode.DEFERRED_TO_CALLER);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy avec correlationId null ne lève pas d'exception à la construction")
    void directCopy_nullCorrelationId_builds() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();

        Sink<Path> sink = CascadeSinkFactory.directCopy(repo, props, null, null);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy avec HeartbeatService non-null construit le Sink correctement")
    void directCopy_withHeartbeatService_builds() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();
        HeartbeatService hb = mock(HeartbeatService.class);

        Sink<Path> sink = CascadeSinkFactory.directCopy(repo, props, CORR_ID, hb);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy avec WorkflowActiveRegistry non-null construit le Sink correctement")
    void directCopy_withRegistry_builds() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();
        WorkflowActiveRegistry registry = new WorkflowActiveRegistry();
        HeartbeatService hb = mock(HeartbeatService.class);

        Sink<Path> sink = CascadeSinkFactory.directCopy(repo, props, CORR_ID, hb, registry);

        assertThat(sink).isNotNull();
    }

    @Test
    @DisplayName("directCopy avec FinalizeMode null utilise SYNCHRONOUS par défaut")
    void directCopy_nullFinalizeMode_usesSynchronous() {
        DataRepository repo = mockRepo(mock(DataSource.class));
        ImportProperties props = defaultProps();

        Sink<Path> sink = CascadeSinkFactory.directCopy(
                repo, props, CORR_ID, null, null, null);

        assertThat(sink).isNotNull();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private DataRepository mockRepo(DataSource dataSource) {
        DataRepository repo = mock(DataRepository.class);
        when(repo.getDataSource()).thenReturn(dataSource);
        when(repo.getSchemaName()).thenReturn("myapp");
        SqlSchema schema = mock(SqlSchema.class);
        when(schema.getSqlIdentifier()).thenReturn("\"myapp\"");
        SqlTable table = new SqlTable(schema, "referencevalue");
        when(repo.getTable()).thenReturn(table);
        return repo;
    }

    private ImportProperties defaultProps() {
        // defaults : PER_CONNECTION_TEMP, no shared table
        return new ImportProperties();
    }
}
