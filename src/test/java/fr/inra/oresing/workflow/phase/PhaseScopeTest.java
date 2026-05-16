package fr.inra.oresing.workflow.phase;

import fr.inra.oresing.workflow.WorkflowPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PhaseScope} - verifies the AutoCloseable
 * lifecycle , the sequential transition order , exception propagation ,
 * and the null-tracker no-op safety .
 */
class PhaseScopeTest {

    private WorkflowPhaseTracker tracker;
    private UUID                 cid;

    @BeforeEach
    void setUp() {
        tracker = mock(WorkflowPhaseTracker.class);
        cid     = UUID.randomUUID();
    }

    @Test
    void run_runnable_publishes_phase_then_executes_work() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);

        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            scope.run(WorkflowPhase.COUNTING_ROWS, () -> executed.set(true));
        }

        assertThat(executed).isTrue();
        verify(tracker).transitionTo(cid, WorkflowPhase.COUNTING_ROWS);
    }

    @Test
    void run_supplier_returns_value_and_publishes_phase() throws Exception {
        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            long result = scope.run(WorkflowPhase.COUNTING_ROWS, () -> 42L);
            assertThat(result).isEqualTo(42L);
        }
        verify(tracker).transitionTo(cid, WorkflowPhase.COUNTING_ROWS);
    }

    @Test
    void sequential_runs_emit_phases_in_order() throws Exception {
        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            scope.run(WorkflowPhase.COUNTING_ROWS, () -> { });
            scope.run(WorkflowPhase.DELETE_ROWS,   () -> { });
            scope.run(WorkflowPhase.DELETE_FILE_ROW, () -> { });
        }
        InOrder inOrder = inOrder(tracker);
        inOrder.verify(tracker).transitionTo(cid, WorkflowPhase.COUNTING_ROWS);
        inOrder.verify(tracker).transitionTo(cid, WorkflowPhase.DELETE_ROWS);
        inOrder.verify(tracker).transitionTo(cid, WorkflowPhase.DELETE_FILE_ROW);
    }

    @Test
    void currentPhase_reflects_last_transition() throws Exception {
        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            assertThat(scope.currentPhase()).isNull();
            scope.run(WorkflowPhase.COUNTING_ROWS, () -> { });
            assertThat(scope.currentPhase()).isEqualTo(WorkflowPhase.COUNTING_ROWS);
            scope.run(WorkflowPhase.DELETE_ROWS, () -> { });
            assertThat(scope.currentPhase()).isEqualTo(WorkflowPhase.DELETE_ROWS);
        }
    }

    @Test
    void close_resets_current_phase() throws Exception {
        PhaseScope scope = PhaseScope.open(tracker, cid);
        scope.run(WorkflowPhase.COUNTING_ROWS, () -> { });
        scope.close();
        assertThat(scope.currentPhase()).isNull();
    }

    @Test
    void exception_in_work_propagates_and_scope_still_closes() {
        AtomicInteger transitionCount = new AtomicInteger();
        doAnswer(inv -> { transitionCount.incrementAndGet(); return null; })
                .when(tracker).transitionTo(any(UUID.class), anyString());

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
                scope.run(WorkflowPhase.COUNTING_ROWS, () -> { throw new IllegalStateException("boom"); });
            }
        }).withMessage("boom");

        // Phase was still emitted before the exception .
        assertThat(transitionCount.get()).isEqualTo(1);
        verify(tracker).transitionTo(cid, WorkflowPhase.COUNTING_ROWS);
    }

    @Test
    void null_tracker_is_noop_safe() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);

        try (PhaseScope scope = PhaseScope.open(null, cid)) {
            scope.run(WorkflowPhase.COUNTING_ROWS, () -> executed.set(true));
        }

        assertThat(executed).isTrue();
        // No tracker calls because tracker is null .
    }

    @Test
    void transitionTo_can_be_used_manually_without_run() {
        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            scope.transitionTo(WorkflowPhase.COUNTING_ROWS);
            scope.transitionTo(WorkflowPhase.DELETE_ROWS);
        }
        InOrder inOrder = inOrder(tracker);
        inOrder.verify(tracker).transitionTo(cid, WorkflowPhase.COUNTING_ROWS);
        inOrder.verify(tracker).transitionTo(cid, WorkflowPhase.DELETE_ROWS);
    }

    @Test
    void close_without_any_run_is_safe() {
        try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
            // no-op : just open and close without any phase
        }
        verifyNoInteractions(tracker);
    }

    @Test
    void supplier_exception_propagates_with_original_type() {
        assertThatExceptionOfType(java.io.IOException.class).isThrownBy(() -> {
            try (PhaseScope scope = PhaseScope.open(tracker, cid)) {
                scope.run(WorkflowPhase.COUNTING_ROWS, () -> {
                    throw new java.io.IOException("disk full");
                });
            }
        }).withMessage("disk full");
    }
}
