package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link WorkflowLogRetentionTask}.
 */
@Tag("domain.model")
@DisplayName("WorkflowLogRetentionTask — purge périodique workflow_log")
class WorkflowLogRetentionTaskTest {

    @Test
    @DisplayName("purgeOldEntries() appelle deleteOlderThan avec le bon nombre de jours")
    void purgeCallsRepository() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.deleteOlderThan(30)).thenReturn(5);

        WorkflowLogRetentionTask task = new WorkflowLogRetentionTask(repo, 30);
        task.purgeOldEntries();

        verify(repo, times(1)).deleteOlderThan(30);
    }

    @Test
    @DisplayName("purgeOldEntries() avec 0 deletions n'est pas une erreur")
    void purgeZeroEntries() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.deleteOlderThan(7)).thenReturn(0);

        WorkflowLogRetentionTask task = new WorkflowLogRetentionTask(repo, 7);
        assertThatCode(task::purgeOldEntries).doesNotThrowAnyException();

        verify(repo, times(1)).deleteOlderThan(7);
    }

    @Test
    @DisplayName("purgeOldEntries() absorbe les exceptions DB (ne casse pas le scheduler)")
    void purgeSwallowsException() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        doThrow(new RuntimeException("DB error")).when(repo).deleteOlderThan(anyInt());

        WorkflowLogRetentionTask task = new WorkflowLogRetentionTask(repo, 30);
        assertThatCode(task::purgeOldEntries).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("constructeur avec retentionDays différent est respecté")
    void customRetentionDays() {
        WorkflowLogRepository repo = mock(WorkflowLogRepository.class);
        when(repo.deleteOlderThan(90)).thenReturn(100);

        WorkflowLogRetentionTask task = new WorkflowLogRetentionTask(repo, 90);
        task.purgeOldEntries();

        verify(repo, times(1)).deleteOlderThan(90);
    }
}
