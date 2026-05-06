package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs d'ImportProperties (POJO de configuration).
 * Aucun contexte Spring.
 */
@DisplayName("ImportProperties – valeurs par défaut et setters")
@Tag("domain.model")
class ImportPropertiesTest {

    @Test
    @DisplayName("Les valeurs par défaut correspondent aux javadocs")
    void defaultValues() {
        ImportProperties props = new ImportProperties();
        assertThat(props.getChunkSizeLines()).isEqualTo(1000);
        assertThat(props.getParallelism()).isEqualTo(4);
        assertThat(props.getProgressBatchSize()).isEqualTo(100);
        assertThat(props.getMaxErrorsThreshold()).isEqualTo(100);
        assertThat(props.getChunksTempDir()).isEqualTo("/tmp/openadom-import/chunks");
        assertThat(props.getProcessedTempDir()).isEqualTo("/tmp/openadom-import/processed");
        assertThat(props.getCollectorChunkSize()).isZero();
        assertThat(props.isEnableMetrics()).isFalse();
        assertThat(props.getReferenceCacheMaxEntries()).isEqualTo(5000);
        assertThat(props.getGroovyCacheMaxEntries()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Les setters modifient correctement les champs")
    void setters() {
        ImportProperties props = new ImportProperties();
        props.setChunkSizeLines(500);
        props.setParallelism(8);
        props.setProgressBatchSize(50);
        props.setMaxErrorsThreshold(200);
        props.setChunksTempDir("/data/chunks");
        props.setProcessedTempDir("/data/processed");
        props.setCollectorChunkSize(10);
        props.setEnableMetrics(true);
        props.setReferenceCacheMaxEntries(3000);
        props.setGroovyCacheMaxEntries(500);

        assertThat(props.getChunkSizeLines()).isEqualTo(500);
        assertThat(props.getParallelism()).isEqualTo(8);
        assertThat(props.getProgressBatchSize()).isEqualTo(50);
        assertThat(props.getMaxErrorsThreshold()).isEqualTo(200);
        assertThat(props.getChunksTempDir()).isEqualTo("/data/chunks");
        assertThat(props.getProcessedTempDir()).isEqualTo("/data/processed");
        assertThat(props.getCollectorChunkSize()).isEqualTo(10);
        assertThat(props.isEnableMetrics()).isTrue();
        assertThat(props.getReferenceCacheMaxEntries()).isEqualTo(3000);
        assertThat(props.getGroovyCacheMaxEntries()).isEqualTo(500);
    }
}