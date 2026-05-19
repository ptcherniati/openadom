package fr.inra.oresing.workflow.cascade.config;

import fr.inrae.ore.cascade.model.workflow.PipelineMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires {@link PublishProperties} + conversion vers
 * {@link CascadeRuntimeOverride} .
 *
 * <p>Verifie :
 * <ul>
 *   <li>defauts memoire-friendly attendus ( PIPELINED + chunk 200 +
 *       parallelism 2 + MERGE_FILE + SHARED_UNLOGGED + 100 errors ) ;</li>
 *   <li>setters fonctionnels ( hot-mutation supportee via
 *       ConfigFieldRegistry ) ;</li>
 *   <li>{@code toRuntimeOverride()} renvoie un record contenant les 6
 *       fields , non-null ( pour qu'ils surchargent ImportProperties ) .</li>
 * </ul>
 */
class PublishPropertiesTest {

    @Test
    void defaultValues_areMemoryFriendly() {
        PublishProperties p = new PublishProperties();
        // DIRECT_COPY + PIPELINED : seule combinaison ou pipelineMode
        // PIPELINED prend effet ( MERGE_FILE force STAGED cote backend ) .
        assertThat(p.getPipelineMode()).isEqualTo(PipelineMode.PIPELINED);
        assertThat(p.getSinkStrategy()).isEqualTo(ImportProperties.SinkStrategy.DIRECT_COPY);
        assertThat(p.getStagingStrategy()).isEqualTo(ImportProperties.StagingStrategy.SHARED_UNLOGGED);
        assertThat(p.getChunkSizeLines()).isEqualTo(1000);
        assertThat(p.getParallelism()).isEqualTo(2);
        assertThat(p.getMaxErrorsThreshold()).isEqualTo(100);
    }

    @Test
    void setters_mutateState() {
        PublishProperties p = new PublishProperties();
        p.setChunkSizeLines(500);
        p.setParallelism(4);
        p.setMaxErrorsThreshold(50);
        p.setPipelineMode(PipelineMode.STAGED);
        p.setSinkStrategy(ImportProperties.SinkStrategy.DIRECT_COPY);
        p.setStagingStrategy(ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE);

        assertThat(p.getChunkSizeLines()).isEqualTo(500);
        assertThat(p.getParallelism()).isEqualTo(4);
        assertThat(p.getMaxErrorsThreshold()).isEqualTo(50);
        assertThat(p.getPipelineMode()).isEqualTo(PipelineMode.STAGED);
        assertThat(p.getSinkStrategy()).isEqualTo(ImportProperties.SinkStrategy.DIRECT_COPY);
        assertThat(p.getStagingStrategy()).isEqualTo(ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE);
    }

    @Test
    void toRuntimeOverride_allFieldsPresent() {
        PublishProperties p = new PublishProperties();
        CascadeRuntimeOverride o = p.toRuntimeOverride();
        // Tous les fields doivent etre presents ( != null ) pour
        // surcharger effectivement ImportProperties dans le pipeline .
        assertThat(o.pipelineMode()).isEqualTo(PipelineMode.PIPELINED);
        assertThat(o.sinkStrategy()).isEqualTo(ImportProperties.SinkStrategy.DIRECT_COPY);
        assertThat(o.stagingStrategy()).isEqualTo(ImportProperties.StagingStrategy.SHARED_UNLOGGED);
        assertThat(o.parallelism()).isEqualTo(2);
        assertThat(o.chunkSizeLines()).isEqualTo(1000);
        assertThat(o.maxErrorsThreshold()).isEqualTo(100);
        assertThat(o.hasAnyOverride()).isTrue();
    }

    @Test
    void emptyOverride_hasNoFields() {
        CascadeRuntimeOverride empty = CascadeRuntimeOverride.empty();
        assertThat(empty.pipelineMode()).isNull();
        assertThat(empty.sinkStrategy()).isNull();
        assertThat(empty.stagingStrategy()).isNull();
        assertThat(empty.parallelism()).isNull();
        assertThat(empty.chunkSizeLines()).isNull();
        assertThat(empty.maxErrorsThreshold()).isNull();
        assertThat(empty.hasAnyOverride()).isFalse();
    }
}
