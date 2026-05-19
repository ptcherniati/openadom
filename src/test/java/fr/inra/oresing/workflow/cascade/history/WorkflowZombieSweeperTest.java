package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link WorkflowZombieSweeper} . Couvre :
 *
 * <ul>
 *   <li>delegation propre vers {@link WorkflowLogRepository#markZombies}</li>
 *   <li>swallow d'exceptions ( un sweep en echec ne doit pas casser le
 *       scheduler , on retentera au prochain tick )</li>
 *   <li>passage du seuil configurable</li>
 * </ul>
 */
@DisplayName("WorkflowZombieSweeper")
class WorkflowZombieSweeperTest {

    /** Factory helper pour instancier le sweeper sans mail ni base URL . */
    @SuppressWarnings("unchecked")
    private static WorkflowZombieSweeper sweeper(WorkflowLogRepository repo,
                                                  int threshold, boolean cleanupOnBoot) {
        ObjectProvider emailProvider = mock(ObjectProvider.class);
        return new WorkflowZombieSweeper(repo, threshold, cleanupOnBoot,
                emailProvider, "", "");
    }

    @Test
    @DisplayName("sweepZombies ( ) appelle markZombies avec le seuil configure")
    void sweep_invokes_repository_with_threshold() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.markZombies(45)).thenReturn(2);

        WorkflowZombieSweeper sweeper = sweeper(repo, 45, false);
        sweeper.sweepZombies();

        verify(repo, times(1)).markZombies(45);
    }

    @Test
    @DisplayName("sweepZombies ( ) survit a une exception du repository ( retry au prochain tick )")
    void sweep_swallows_runtime_exception() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        doThrow(new RuntimeException("DB down")).when(repo).markZombies(30);

        WorkflowZombieSweeper sweeper = sweeper(repo, 30, false);

        // Sweeper ne doit JAMAIS propager l'exception ( sinon le scheduler
        // Spring suspend la tache jusqu'au prochain redemarrage ) .
        assertThatCode(sweeper::sweepZombies).doesNotThrowAnyException();
        verify(repo, times(1)).markZombies(30);
    }

    @Test
    @DisplayName("sweepZombies ( ) avec 0 zombies ne loggue qu'en debug")
    void sweep_zero_result_silent() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.markZombies(60)).thenReturn(0);

        WorkflowZombieSweeper sweeper = sweeper(repo, 60, false);
        sweeper.sweepZombies();

        verify(repo, times(1)).markZombies(60);
        verify(repo, never()).deleteOlderThan(60);
    }

    @Test
    @DisplayName("constructor stocke le seuil ( log info au boot )")
    void constructor_logs_config() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        WorkflowZombieSweeper s = sweeper(repo, 15, false);
        assertThatCode(s::sweepZombies).doesNotThrowAnyException();
    }
}
