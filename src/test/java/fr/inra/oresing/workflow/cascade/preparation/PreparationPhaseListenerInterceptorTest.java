package fr.inra.oresing.workflow.cascade.preparation;

import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inrae.ore.cascade.model.interceptor.preparation.PreparationInterceptorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link PreparationPhaseListenerInterceptor} . Cover the
 * routing of sub-phase events to {@code workflowLogRepository.updatePhase}
 * with the right cid + phase string , the defensive guards ( null cid ,
 * null phase , malformed UUID , missing repository bean ) , and the
 * best-effort behaviour ( runtime exception in the repository does NOT
 * bubble back to the preparator ) .
 */
class PreparationPhaseListenerInterceptorTest {

    private PreparationPhaseListenerInterceptor listener;
    private WorkflowLogRepository                repo;

    @BeforeEach
    void setUp() {
        repo     = mock(WorkflowLogRepository.class);
        listener = new PreparationPhaseListenerInterceptor(repo);
    }

    @Test
    void onSubPhase_routes_to_updatePhase_with_parsed_uuid() {
        UUID cid = UUID.randomUUID();
        listener.onSubPhase(cid.toString(), "CSV_REENCODING", Instant.now());

        ArgumentCaptor<UUID> cidCap = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> phaseCap = ArgumentCaptor.forClass(String.class);
        verify(repo).updatePhase(cidCap.capture(), phaseCap.capture());
        assertThat(cidCap.getValue()).isEqualTo(cid);
        assertThat(phaseCap.getValue()).isEqualTo("CSV_REENCODING");
    }

    @Test
    void null_correlation_id_is_ignored() {
        listener.onSubPhase(null, "PHASE_X", Instant.now());
        verifyNoInteractions(repo);
    }

    @Test
    void null_phase_is_ignored() {
        listener.onSubPhase(UUID.randomUUID().toString(), null, Instant.now());
        verifyNoInteractions(repo);
    }

    @Test
    void malformed_correlation_id_is_ignored() {
        listener.onSubPhase("not-a-uuid", "PHASE_X", Instant.now());
        verifyNoInteractions(repo);
    }

    @Test
    void missing_repository_bean_makes_listener_a_noop() {
        // Simulate a context without WorkflowLogRepository ( unit tests of
        // cascade isolees , profil sans persistence ) . The listener must
        // silently no-op without throwing , so the preparator workflow
        // is never broken by an observability misconfiguration .
        PreparationPhaseListenerInterceptor empty = new PreparationPhaseListenerInterceptor(null);
        empty.onSubPhase(UUID.randomUUID().toString(), "PHASE_Y", Instant.now());
        // No exception , no verify needed
    }

    @Test
    void repository_exception_is_swallowed_best_effort() {
        UUID cid = UUID.randomUUID();
        doThrow(new RuntimeException("DB unavailable"))
                .when(repo).updatePhase(eq(cid), any());
        // Must NOT throw - the preparator continues running .
        listener.onSubPhase(cid.toString(), "PHASE_Z", Instant.now());
        verify(repo).updatePhase(eq(cid), eq("PHASE_Z"));
    }

    @Test
    void lifecycle_hooks_are_silent_no_ops() {
        // beforePreparation / afterPreparation / onPreparationError just
        // log ; they must not interact with the repository .
        PreparationInterceptorContext ctx = new PreparationInterceptorContext(
                UUID.randomUUID().toString(),
                "alice",
                Instant.now(),
                Map.of());
        listener.beforePreparation(ctx);
        listener.afterPreparation(ctx);
        listener.onPreparationError(new RuntimeException("boom"), ctx);
        verify(repo, never()).updatePhase(any(), any());
    }
}
