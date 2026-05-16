package fr.inra.oresing.workflow.phase;

import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkflowProgressReporter} - verifies the 3
 * symmetric reporting calls ( total / progress / completed ) propagate
 * correctly to {@link WorkflowActiveRegistry} with the proper
 * dashboard cid resolution .
 */
class WorkflowProgressReporterTest {

    private WorkflowActiveRegistry          registry;
    private PublishLifecycleCoordinator     coordinator;
    private WorkflowProgressReporter        reporter;

    @BeforeEach
    void setUp() {
        registry    = mock(WorkflowActiveRegistry.class);
        coordinator = mock(PublishLifecycleCoordinator.class);
        reporter    = new WorkflowProgressReporter(registry, coordinator);
    }

    @Test
    void publish_workflow_resolves_to_child_cid() {
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.of(child));

        reporter.reportTotal(parent, 1000L);

        verify(registry).setRecordsTotal(child, 1000L);
        verifyNoMoreInteractions(registry);
    }

    @Test
    void unpublish_workflow_falls_back_to_parent_cid() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());

        reporter.reportTotal(parent, 500L);

        verify(registry).setRecordsTotal(parent, 500L);
    }

    @Test
    void reportProgress_invokes_setFinalRowsAuthoritative() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());

        reporter.reportProgress(parent, 250L);

        verify(registry).setFinalRowsAuthoritative(parent, 250L);
    }

    @Test
    void reportCompleted_emits_both_total_and_progress_with_same_value() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());

        reporter.reportCompleted(parent, 870_720L);

        verify(registry).setRecordsTotal(parent, 870_720L);
        verify(registry).setFinalRowsAuthoritative(parent, 870_720L);
        verifyNoMoreInteractions(registry);
    }

    @Test
    void null_correlation_id_is_noop_for_all_methods() {
        reporter.reportTotal(null, 100L);
        reporter.reportProgress(null, 100L);
        reporter.reportCompleted(null, 100L);

        verifyNoInteractions(registry, coordinator);
    }

    @Test
    void negative_total_is_rejected() {
        reporter.reportTotal(UUID.randomUUID(), -1L);

        verifyNoInteractions(registry);
    }

    @Test
    void negative_progress_is_rejected() {
        reporter.reportProgress(UUID.randomUUID(), -5L);

        verifyNoInteractions(registry);
    }

    @Test
    void negative_completed_count_is_rejected() {
        reporter.reportCompleted(UUID.randomUUID(), -1L);

        verifyNoInteractions(registry);
    }

    @Test
    void zero_count_is_valid() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());

        reporter.reportCompleted(parent, 0L);

        verify(registry).setRecordsTotal(parent, 0L);
        verify(registry).setFinalRowsAuthoritative(parent, 0L);
    }

    @Test
    void runtimeException_from_registry_is_swallowed() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("simulated registry down"))
                .when(registry).setRecordsTotal(parent, 100L);

        // Must NOT throw - reporter is best-effort .
        reporter.reportTotal(parent, 100L);

        verify(registry).setRecordsTotal(parent, 100L);
    }

    @Test
    void null_coordinator_falls_back_to_parent_cid() {
        WorkflowProgressReporter reporterNoCoord = new WorkflowProgressReporter(registry, null);
        UUID parent = UUID.randomUUID();

        reporterNoCoord.reportTotal(parent, 42L);

        verify(registry).setRecordsTotal(parent, 42L);
    }
}
