package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests complémentaires pour {@link WorkflowZombieSweeper} :
 * getThresholdMinutes / setThresholdMinutes et validation des invariants.
 */
@Tag("domain.model")
@DisplayName("WorkflowZombieSweeper — configuration dynamique du seuil")
class WorkflowZombieSweeperConfigTest {

    private WorkflowLogRepository repo() {
        return mock(WorkflowLogRepository.class);
    }

    @Test
    @DisplayName("getThresholdMinutes() retourne la valeur passée au constructeur")
    void constructorStoresThreshold() {
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo(), 20);
        assertThat(sweeper.getThresholdMinutes()).isEqualTo(20);
    }

    @Test
    @DisplayName("setThresholdMinutes() met à jour le seuil utilisé par sweepZombies()")
    void setThresholdUpdatesValue() {
        WorkflowLogRepository repo = repo();
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo, 10);

        sweeper.setThresholdMinutes(25);

        assertThat(sweeper.getThresholdMinutes()).isEqualTo(25);
        sweeper.sweepZombies();
        verify(repo).markZombies(25);
        verify(repo, never()).markZombies(10);
    }

    @Test
    @DisplayName("setThresholdMinutes(0) lève IllegalArgumentException")
    void setThresholdZeroThrows() {
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo(), 10);
        assertThatThrownBy(() -> sweeper.setThresholdMinutes(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 1");
    }

    @Test
    @DisplayName("setThresholdMinutes(-1) lève IllegalArgumentException")
    void setThresholdNegativeThrows() {
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo(), 10);
        assertThatThrownBy(() -> sweeper.setThresholdMinutes(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setThresholdMinutes(1) est la valeur minimale acceptée")
    void setThresholdOneIsValid() {
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo(), 10);
        sweeper.setThresholdMinutes(1);
        assertThat(sweeper.getThresholdMinutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("setThresholdMinutes() n'appelle pas markZombies() immédiatement")
    void setThresholdDoesNotTriggerSweep() {
        WorkflowLogRepository repo = repo();
        WorkflowZombieSweeper sweeper = new WorkflowZombieSweeper(repo, 10);

        sweeper.setThresholdMinutes(30);

        verify(repo, never()).markZombies(anyInt());
    }
}
