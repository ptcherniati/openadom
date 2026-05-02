package fr.inra.oresing.workflow.cascade.staging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests le parsing nom de table -> UUID utilise par le sweeper pour
 * remap les tables orphelines vers les workflows actifs .
 *
 * @author R.YAHIAOUI
 */
class StagingOrphanSweeperTest {

    @Test
    void parseCorrelationId_handles_canonical_form() {
        UUID expected = UUID.fromString("11dbf758-b670-43bc-9c91-aa64544016ae");
        UUID got = StagingOrphanSweeper.parseCorrelationId(
                "referencevalue_import_11dbf758_b670_43bc_9c91_aa64544016ae");
        assertThat(got).isEqualTo(expected);
    }

    @Test
    void parseCorrelationId_returns_null_for_shared_table() {
        assertThat(StagingOrphanSweeper.parseCorrelationId("referencevalue_import_shared")).isNull();
    }

    @Test
    void parseCorrelationId_returns_null_for_garbage() {
        assertThat(StagingOrphanSweeper.parseCorrelationId(null)).isNull();
        assertThat(StagingOrphanSweeper.parseCorrelationId("foo")).isNull();
        assertThat(StagingOrphanSweeper.parseCorrelationId("referencevalue_import_garbage")).isNull();
        assertThat(StagingOrphanSweeper.parseCorrelationId("referencevalue_import_xx_yy_zz_ww_qq")).isNull();
    }
}
