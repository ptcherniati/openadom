package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.config.PoolReloader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link PipelinePoolsDTO} et son sous-record {@link PipelinePoolsDTO.PoolDTO}.
 */
@Tag("domain.model")
@DisplayName("PipelinePoolsDTO — snapshot idle des pools cascade")
class PipelinePoolsDtoTest {

    // ─── PoolDTO.from(null) ───────────────────────────────────────────────────

    @Test
    @DisplayName("PoolDTO.from(null) → UNKNOWN pool avec valeurs à 0")
    void poolDtoFromNull() {
        PipelinePoolsDTO.PoolDTO dto = PipelinePoolsDTO.PoolDTO.from(null);
        assertThat(dto.stage()).isEqualTo("UNKNOWN");
        assertThat(dto.corePoolSize()).isZero();
        assertThat(dto.activeCount()).isZero();
        assertThat(dto.queueSize()).isZero();
        assertThat(dto.queueCapacity()).isZero();
    }

    // ─── PoolDTO.from(snapshot) ───────────────────────────────────────────────

    @Test
    @DisplayName("PoolDTO.from(snapshot) → tous les champs mappés")
    void poolDtoFromSnapshot() {
        PoolReloader.PoolSnapshot snap = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.TRANSFORM, 4, 4, 2, 4, 10, 50);
        PipelinePoolsDTO.PoolDTO dto = PipelinePoolsDTO.PoolDTO.from(snap);

        assertThat(dto.stage()).isEqualTo("TRANSFORM");
        assertThat(dto.corePoolSize()).isEqualTo(4);
        assertThat(dto.activeCount()).isEqualTo(2);
        assertThat(dto.queueSize()).isEqualTo(10);
        assertThat(dto.queueCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("PoolDTO.from(SOURCE) → stage SOURCE")
    void poolDtoSource() {
        PoolReloader.PoolSnapshot snap = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.SOURCE, 1, 1, 0, 1, 0, 10);
        assertThat(PipelinePoolsDTO.PoolDTO.from(snap).stage()).isEqualTo("SOURCE");
    }

    @Test
    @DisplayName("PoolDTO.from(SINK) → stage SINK")
    void poolDtoSink() {
        PoolReloader.PoolSnapshot snap = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.SINK, 2, 2, 1, 2, 5, 100);
        assertThat(PipelinePoolsDTO.PoolDTO.from(snap).stage()).isEqualTo("SINK");
    }

    // ─── PipelinePoolsDTO root ────────────────────────────────────────────────

    @Test
    @DisplayName("PipelinePoolsDTO root : tous les champs accessibles")
    void rootRecord() {
        PipelinePoolsDTO.PoolDTO src  = PipelinePoolsDTO.PoolDTO.from(null);
        PipelinePoolsDTO.PoolDTO trx  = PipelinePoolsDTO.PoolDTO.from(
                new PoolReloader.PoolSnapshot(PoolReloader.Stage.TRANSFORM, 4, 4, 2, 4, 0, 50));
        PipelinePoolsDTO.PoolDTO snk  = PipelinePoolsDTO.PoolDTO.from(null);
        PipelinePoolsDTO.PoolDTO ord  = PipelinePoolsDTO.PoolDTO.from(null);

        PipelinePoolsDTO dto = new PipelinePoolsDTO(src, trx, snk, ord);
        assertThat(dto.source()).isSameAs(src);
        assertThat(dto.transform()).isSameAs(trx);
        assertThat(dto.sink()).isSameAs(snk);
        assertThat(dto.ordering()).isSameAs(ord);
        assertThat(dto.transform().corePoolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("PipelinePoolsDTO : record equality")
    void equality() {
        PipelinePoolsDTO.PoolDTO pool = PipelinePoolsDTO.PoolDTO.from(null);
        PipelinePoolsDTO a = new PipelinePoolsDTO(pool, pool, pool, pool);
        PipelinePoolsDTO b = new PipelinePoolsDTO(pool, pool, pool, pool);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
