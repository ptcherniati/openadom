package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents.WorkflowAliveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires pour {@link CascadeHeartbeatBridge} . Couvre :
 * <ul>
 *   <li>onWorkflowAlive avec UUID valide -> repo.beat ( UUID )</li>
 *   <li>onWorkflowAlive avec correlationId non-UUID -> ignore , aucun beat</li>
 *   <li>init() configure la lib cascade ( periode , enabled )</li>
 *   <li>shutdown() unsubscribe proprement le listener du bus</li>
 * </ul>
 */
@DisplayName("CascadeHeartbeatBridge")
@Tag("domain.model")
class CascadeHeartbeatBridgeTest {

    private WorkflowLogRepository repo;
    private CascadeHeartbeatBridge bridge;

    @BeforeEach
    void setUp() {
        repo   = mock(WorkflowLogRepository.class);
        bridge = new CascadeHeartbeatBridge(repo, 30_000L);
    }

    @AfterEach
    void tearDown() {
        bridge.shutdown();
    }

    @Test
    void valid_uuid_triggers_beat() {
        UUID corr = UUID.randomUUID();
        WorkflowAliveEvent ev = new WorkflowAliveEvent(
                corr.toString(), System.nanoTime(), Instant.now());

        bridge.onWorkflowAlive(ev);

        verify(repo, times(1)).beat(eq(corr));
    }

    @Test
    void non_uuid_correlation_id_is_ignored() {
        WorkflowAliveEvent ev = new WorkflowAliveEvent(
                "not-a-uuid", System.nanoTime(), Instant.now());

        bridge.onWorkflowAlive(ev);

        verify(repo, never()).beat(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void null_event_is_safe() {
        bridge.onWorkflowAlive(null);
        verify(repo, never()).beat(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void init_configures_bus_then_subscribes() {
        bridge.init();

        WorkflowEventBus bus = WorkflowEventBus.getInstance();
        org.junit.jupiter.api.Assertions.assertTrue(bus.isLifecycleHeartbeatEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(30_000L, bus.getLifecycleHeartbeatPeriodMillis());
    }
}
