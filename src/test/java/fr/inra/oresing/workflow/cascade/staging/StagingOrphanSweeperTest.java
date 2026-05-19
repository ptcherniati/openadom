package fr.inra.oresing.workflow.cascade.staging;

import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests le parsing nom de table -> UUID utilise par le sweeper pour
 * remap les tables orphelines vers les workflows actifs .
 *
 * @author R.YAHIAOUI
 */
@Tag("domain.model")
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

    // ───────────────────────────────────────────────────────────────────────────
    // sweepPerWorkflowTables
    // ───────────────────────────────────────────────────────────────────────────

    private static StagingOrphanSweeper makeSweeper(JdbcTemplate jdbc, WorkflowActiveRegistry reg) {
        StagingOrphanSweeper s = new StagingOrphanSweeper(jdbc, reg);
        ReflectionTestUtils.setField(s, "sharedOrphanTtlMinutes", 60);
        ReflectionTestUtils.setField(s, "perWorkflowGraceMinutes", 10);
        return s;
    }

    @Test
    @DisplayName("sweepPerWorkflowTables : no tables → returns 0 without any DROP")
    void sweepPerWorkflowTables_empty_list() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        int dropped = makeSweeper(jdbc, reg).sweepPerWorkflowTables();

        assertThat(dropped).isZero();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("sweepPerWorkflowTables : table belongs to active workflow → no DROP")
    void sweepPerWorkflowTables_active_workflow_not_dropped() {
        UUID activeCid = UUID.fromString("11dbf758-b670-43bc-9c91-aa64544016ae");
        String tableName = "referencevalue_import_11dbf758_b670_43bc_9c91_aa64544016ae";

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(tableName));
        WorkflowSnapshot snap = WorkflowSnapshot.minimal(
                activeCid, "IMPORT", UUID.randomUUID(), "user", "app", "data", "file.csv",
                Instant.now(), "IN_PROGRESS", 0, 0, 0, null, 0, 0, List.of(), List.of());
        when(reg.list(null)).thenReturn(List.of(snap));

        int dropped = makeSweeper(jdbc, reg).sweepPerWorkflowTables();

        assertThat(dropped).isZero();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("sweepPerWorkflowTables : orphan table → DROP called")
    void sweepPerWorkflowTables_orphan_table_dropped() {
        String tableName = "referencevalue_import_11dbf758_b670_43bc_9c91_aa64544016ae";

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(tableName));
        when(reg.list(null)).thenReturn(List.of()); // no active workflows

        int dropped = makeSweeper(jdbc, reg).sweepPerWorkflowTables();

        assertThat(dropped).isEqualTo(1);
        verify(jdbc).execute(contains(tableName));
    }

    @Test
    @DisplayName("sweepPerWorkflowTables : DROP failure caught, returns 0")
    void sweepPerWorkflowTables_drop_failure_is_caught() {
        String tableName = "referencevalue_import_11dbf758_b670_43bc_9c91_aa64544016ae";

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(tableName));
        when(reg.list(null)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("permission denied"))
                .when(jdbc).execute(anyString());

        int dropped = makeSweeper(jdbc, reg).sweepPerWorkflowTables();

        assertThat(dropped).isZero(); // DROP failed, not counted
    }

    @Test
    @DisplayName("sweepPerWorkflowTables : table with invalid UUID name is skipped")
    void sweepPerWorkflowTables_invalid_uuid_table_skipped() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of("referencevalue_import_shared"));
        when(reg.list(null)).thenReturn(List.of());

        int dropped = makeSweeper(jdbc, reg).sweepPerWorkflowTables();

        assertThat(dropped).isZero();
        verify(jdbc, never()).execute(anyString());
    }

    // ───────────────────────────────────────────────────────────────────────────
    // sweepSharedUnlogged
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweepSharedUnlogged : shared table absent → returns 0")
    void sweepSharedUnlogged_no_shared_table() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        int deleted = makeSweeper(jdbc, reg).sweepSharedUnlogged();

        assertThat(deleted).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("sweepSharedUnlogged : table present → DELETE executed, count returned")
    void sweepSharedUnlogged_deletes_orphan_rows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(5);

        int deleted = makeSweeper(jdbc, reg).sweepSharedUnlogged();

        assertThat(deleted).isEqualTo(5);
    }

    @Test
    @DisplayName("sweepSharedUnlogged : queryForObject returns null → returns 0")
    void sweepSharedUnlogged_null_count_returns_zero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);

        int deleted = makeSweeper(jdbc, reg).sweepSharedUnlogged();

        assertThat(deleted).isZero();
    }

    // ───────────────────────────────────────────────────────────────────────────
    // sweep() (orchestration + exception guard)
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweep() absorbs RuntimeException without propagating")
    void sweep_does_not_propagate_exception() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowActiveRegistry reg = mock(WorkflowActiveRegistry.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("DB down"));

        StagingOrphanSweeper sweeper = makeSweeper(jdbc, reg);

        // Must not throw
        org.assertj.core.api.Assertions.assertThatCode(sweeper::sweep)
                .doesNotThrowAnyException();
    }
}
