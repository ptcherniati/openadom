package fr.inra.oresing.workflow.cascade.staging;

import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests pour {@link StagingMode} : verifie que chaque strategy
 * expose les bons hooks ( copy columns , correlation_id filter ,
 * SQL CREATE / DROP , table name ) sans depend sur Postgres / cascade lib .
 *
 * @author R.YAHIAOUI
 */
class StagingModeTest {

    private static final UUID CID = UUID.fromString("11dbf758-b670-43bc-9c91-aa64544016ae");
    private static final String SHARED_TABLE = "oa_staging.referencevalue_import_shared";

    @Test
    void perConnectionTemp_no_create_no_drop_no_filter() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.PER_CONNECTION_TEMP,
                CID, SHARED_TABLE, 60);

        assertThat(m.copyColumns()).isEqualTo("data");
        assertThat(m.correlationIdFilter(CID)).isNull();
        assertThat(m.createTableSql()).isNull();
        assertThat(m.dropTableSql()).isNull();
        assertThat(m.tableName()).isEqualTo("referencevalue_import");
    }

    @Test
    void sharedUnlogged_filter_by_correlation_id_no_create_no_drop() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.SHARED_UNLOGGED,
                CID, SHARED_TABLE, 60);

        assertThat(m.copyColumns()).isEqualTo("correlation_id, data");
        assertThat(m.correlationIdFilter(CID)).isEqualTo(CID.toString());
        assertThat(m.createTableSql()).isNull();
        assertThat(m.dropTableSql()).isNull();
        assertThat(m.tableName()).isEqualTo(SHARED_TABLE);
    }

    @Test
    void perWorkflowTable_creates_and_drops_dedicated_table() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE,
                CID, SHARED_TABLE, 60);

        assertThat(m.copyColumns()).isEqualTo("correlation_id, data");
        assertThat(m.correlationIdFilter(CID)).isEqualTo(CID.toString());
        assertThat(m.tableName())
                .isEqualTo("oa_staging.referencevalue_import_11dbf758_b670_43bc_9c91_aa64544016ae");
        assertThat(m.createTableSql())
                .startsWith("CREATE UNLOGGED TABLE IF NOT EXISTS oa_staging.referencevalue_import_")
                .contains("correlation_id uuid NOT NULL")
                .contains("data jsonb NOT NULL");
        assertThat(m.dropTableSql())
                .startsWith("DROP TABLE IF EXISTS oa_staging.referencevalue_import_");
    }

    @Test
    void perWorkflowTable_requires_correlation_id() {
        assertThatThrownBy(() -> StagingMode.of(
                        ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE,
                        null, SHARED_TABLE, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
    }

    @Test
    void perWorkflowTableName_replaces_dashes_with_underscores() {
        UUID u = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertThat(StagingMode.perWorkflowTableName(u))
                .isEqualTo("oa_staging.referencevalue_import_aaaaaaaa_bbbb_cccc_dddd_eeeeeeeeeeee");
    }
}
