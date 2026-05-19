package fr.inra.oresing.workflow.phase;

import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator;
import fr.inra.oresing.workflow.WorkflowPhase;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkflowPhaseTracker} - verifies the dual emission
 * ( persistent workflow_log + in-memory registry ) and the correct
 * resolution of dashboard cid via the coordinator .
 */
class WorkflowPhaseTrackerTest {

    private WorkflowLogRepository      logRepository;
    private WorkflowActiveRegistry     registry;
    private PublishLifecycleCoordinator coordinator;
    private WorkflowPhaseTracker       tracker;

    @BeforeEach
    void setUp() {
        logRepository = mock(WorkflowLogRepository.class);
        registry      = mock(WorkflowActiveRegistry.class);
        coordinator   = mock(PublishLifecycleCoordinator.class);
        tracker       = new WorkflowPhaseTracker(logRepository, registry, coordinator);
    }

    @Test
    void publish_workflow_uses_child_cid_for_registry() {
        UUID parent = UUID.randomUUID();
        UUID child  = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.of(child));

        tracker.transitionTo(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        verify(logRepository).updatePhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);
        verify(registry).setSubPhase(child, WorkflowPhase.SYNTHESIS_REBUILD);
        verifyNoMoreInteractions(logRepository, registry);
    }

    @Test
    void unpublish_workflow_falls_back_to_parent_cid_when_no_child_registered() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());

        tracker.transitionTo(parent, WorkflowPhase.DELETE_ROWS);

        verify(logRepository).updatePhase(parent, WorkflowPhase.DELETE_ROWS);
        verify(registry).setSubPhase(parent, WorkflowPhase.DELETE_ROWS);
    }

    @Test
    void null_correlation_id_is_noop() {
        tracker.transitionTo(null, WorkflowPhase.SYNTHESIS_REBUILD);

        verifyNoInteractions(logRepository, registry, coordinator);
    }

    @Test
    void null_phase_is_noop() {
        tracker.transitionTo(UUID.randomUUID(), null);

        verifyNoInteractions(logRepository, registry, coordinator);
    }

    @Test
    void logRepository_failure_does_not_block_registry_update() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("simulated db down"))
                .when(logRepository).updatePhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        // Should not throw - tracker swallows + logs , still updates registry .
        tracker.transitionTo(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        verify(registry).setSubPhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);
    }

    @Test
    void registry_failure_does_not_block_logRepository_update() {
        UUID parent = UUID.randomUUID();
        when(coordinator.getChildImport(parent)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("simulated registry down"))
                .when(registry).setSubPhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        tracker.transitionTo(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        verify(logRepository).updatePhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);
    }

    @Test
    void null_coordinator_falls_back_to_parent_cid() {
        WorkflowPhaseTracker trackerNoCoord = new WorkflowPhaseTracker(logRepository, registry, null);
        UUID parent = UUID.randomUUID();

        trackerNoCoord.transitionTo(parent, WorkflowPhase.SYNTHESIS_REBUILD);

        verify(logRepository).updatePhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);
        verify(registry).setSubPhase(parent, WorkflowPhase.SYNTHESIS_REBUILD);
    }
}
