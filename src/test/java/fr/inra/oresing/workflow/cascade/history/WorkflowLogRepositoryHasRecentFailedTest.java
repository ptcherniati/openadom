package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkflowLogRepository#hasRecentFailedUnpublish}
 * - verifies null guards , parameter binding , SQL shape ( EXISTS query
 * filtering on workflow_type IN UNPUBLISH/DELETE_FILE + status=FAILED +
 * metadata->>'fileId' + time window ) .
 */
class WorkflowLogRepositoryHasRecentFailedTest {

    private JdbcTemplate           jdbcTemplate;
    private WorkflowLogRepository  repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository   = new WorkflowLogRepository(jdbcTemplate);
    }

    @Test
    void null_fileId_returns_false_without_db_call() {
        assertThat(repository.hasRecentFailedUnpublish(null, 24)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void zero_or_negative_window_returns_false_without_db_call() {
        UUID fileId = UUID.randomUUID();
        assertThat(repository.hasRecentFailedUnpublish(fileId, 0)).isFalse();
        assertThat(repository.hasRecentFailedUnpublish(fileId, -5)).isFalse();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void existing_failed_unpublish_returns_true() {
        UUID fileId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(Boolean.TRUE);

        assertThat(repository.hasRecentFailedUnpublish(fileId, 24)).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), eq(Boolean.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("SELECT EXISTS");
        assertThat(sql).contains("status = 'FAILED'");
        assertThat(sql).contains("workflow_type IN ('UNPUBLISH', 'DELETE_FILE')");
        assertThat(sql).contains("metadata->>'fileId' = ?");
        assertThat(sql).contains("end_time > now() - (? || ' hours')::interval");

        Object[] args = argsCaptor.getValue();
        assertThat(args[0]).isEqualTo(fileId.toString());
        assertThat(args[1]).isEqualTo(24);
    }

    @Test
    void no_failed_unpublish_returns_false() {
        UUID fileId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(Boolean.FALSE);

        assertThat(repository.hasRecentFailedUnpublish(fileId, 24)).isFalse();
    }

    @Test
    void null_result_treated_as_false() {
        UUID fileId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any(Object[].class)))
                .thenReturn(null);

        assertThat(repository.hasRecentFailedUnpublish(fileId, 24)).isFalse();
    }
}
