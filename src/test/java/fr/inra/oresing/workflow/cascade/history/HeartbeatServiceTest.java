package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link HeartbeatService} . Couvre :
 *
 * <ul>
 *   <li>start() emet un beat immediat ( delay=0 ) puis periodique</li>
 *   <li>close() arrete le scheduler ( idempotent )</li>
 *   <li>start(null) renvoie NOOP ( pas de side effect )</li>
 *   <li>propagation au registry ( setLastHeartbeat ) en plus du repository</li>
 *   <li>Heartbeat AutoCloseable usable en try-with-resources</li>
 * </ul>
 */
@DisplayName("HeartbeatService")
class HeartbeatServiceTest {

    private WorkflowLogRepository  repo;
    private WorkflowActiveRegistry registry;
    private HeartbeatService       service;

    @BeforeEach
    void setUp() throws Exception {
        repo     = mock(WorkflowLogRepository.class);
        registry = mock(WorkflowActiveRegistry.class);
        when(repo.beat(any(UUID.class))).thenReturn(true);
        // Interval court pour avoir 2-3 beats en 1 sec sans ralentir les tests .
        service  = new HeartbeatService(repo, registry, /*intervalSeconds*/ 1L);
        // @PostConstruct pas declenche en test pur , on l'invoque manuellement .
        Method init = HeartbeatService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    @AfterEach
    void tearDown() throws Exception {
        Method shutdown = HeartbeatService.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(service);
    }

    @Test
    @DisplayName("start ( ) emet un beat immediat puis periodique sur DB et registry")
    void start_emits_beats_immediately_and_periodically() throws Exception {
        UUID corrId = UUID.randomUUID();

        try (HeartbeatService.Heartbeat hb = service.start(corrId)) {
            // 1ere beat doit arriver presque immediatement ( delay=0 ) .
            verify(repo, timeout(1000).times(1)).beat(eq(corrId));
            verify(registry, timeout(1000).times(1)).setLastHeartbeat(eq(corrId), any());
            // 2eme beat apres ~1 sec ( interval ) .
            verify(repo, timeout(2000).atLeast(2)).beat(eq(corrId));
        }
    }

    @Test
    @DisplayName("close ( ) arrete les beats periodiques")
    void close_stops_periodic_beats() throws Exception {
        UUID corrId = UUID.randomUUID();
        AtomicInteger beatsObserved = new AtomicInteger();
        doAnswer(inv -> { beatsObserved.incrementAndGet(); return true; })
                .when(repo).beat(any(UUID.class));

        HeartbeatService.Heartbeat hb = service.start(corrId);
        Thread.sleep(1500);  // ~1-2 beats
        hb.close();
        int afterClose = beatsObserved.get();
        Thread.sleep(2000);  // attendre 2 sec : aucun beat supplementaire ne doit arriver
        assertThat(beatsObserved.get()).isEqualTo(afterClose);
    }

    @Test
    @DisplayName("close ( ) est idempotent ( double-close ne plante pas )")
    void close_idempotent() {
        UUID corrId = UUID.randomUUID();
        HeartbeatService.Heartbeat hb = service.start(corrId);
        hb.close();
        hb.close();   // doit etre no-op , pas d'exception
    }

    @Test
    @DisplayName("start ( null ) renvoie NOOP ( pas de beat )")
    void start_null_correlation_id_returns_noop() {
        try (HeartbeatService.Heartbeat hb = service.start(null)) {
            // No assertion ; pas de NPE .
        }
        verify(repo, never()).beat(any(UUID.class));
        verify(registry, never()).setLastHeartbeat(any(), any());
    }

    @Test
    @DisplayName("Heartbeat usable en try-with-resources ( cleanup automatique )")
    void try_with_resources_auto_cleanup() throws Exception {
        UUID corrId = UUID.randomUUID();
        try (HeartbeatService.Heartbeat hb = service.start(corrId)) {
            verify(repo, timeout(1000).atLeast(1)).beat(eq(corrId));
        }
        // Apres le bloc , au plus 1 beat supplementaire en cours d'execution .
        Thread.sleep(2000);
        verify(repo, atMost(3)).beat(eq(corrId));  // borne genereuse pour CI lent
    }
}
