package fr.inra.oresing.workflow.cascade.staging;

import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import org.junit.jupiter.api.Tag;
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
@Tag("domain.model")
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
        // SQL = appel a fonction SECURITY DEFINER ( cf V3 migration )
        // pour eviter GRANT CREATE TO PUBLIC sur le schema oa_staging .
        assertThat(m.createTableSql())
                .startsWith("SELECT oa_staging.create_per_workflow_referencevalue_import(")
                .contains(CID.toString());
        assertThat(m.dropTableSql())
                .startsWith("SELECT oa_staging.drop_per_workflow_referencevalue_import(")
                .contains(CID.toString());
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

    @Test
    void perWorkflowTableName_null_throws() {
        assertThatThrownBy(() -> StagingMode.perWorkflowTableName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
    }

    @Test
    void perConnectionTemp_strategy_and_cascadeSpec() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.PER_CONNECTION_TEMP,
                CID, SHARED_TABLE, 60);

        assertThat(m.strategy()).isEqualTo(ImportProperties.StagingStrategy.PER_CONNECTION_TEMP);
        assertThat(m.cascadeSpec()).isNotNull();
    }

    @Test
    void sharedUnlogged_strategy_and_cascadeSpec() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.SHARED_UNLOGGED,
                CID, SHARED_TABLE, 60);

        assertThat(m.strategy()).isEqualTo(ImportProperties.StagingStrategy.SHARED_UNLOGGED);
        assertThat(m.cascadeSpec()).isNotNull();
    }

    @Test
    void perWorkflowTable_strategy_and_cascadeSpec() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE,
                CID, SHARED_TABLE, 60);

        assertThat(m.strategy()).isEqualTo(ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE);
        assertThat(m.cascadeSpec()).isNotNull();
    }

    @Test
    void sharedUnlogged_null_correlationId_filter_returns_null() {
        StagingMode m = StagingMode.of(
                ImportProperties.StagingStrategy.SHARED_UNLOGGED,
                null, SHARED_TABLE, 60);
        assertThat(m.correlationIdFilter(null)).isNull();
    }
}
