package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link WorkflowLogEntry} . Focus sur la factory
 * {@link WorkflowLogEntry#startMarker} qui produit l'entry pre-persistee
 * au demarrage du workflow ( pour fermer le trou d'observabilite SIGKILL ) .
 */
@DisplayName("WorkflowLogEntry")
class WorkflowLogEntryTest {

    @Test
    @DisplayName("startMarker ( ) produit une entry IN_PROGRESS sans champ terminal")
    void startMarker_has_in_progress_status_and_null_terminals() {
        UUID corrId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowLogEntry e = WorkflowLogEntry.startMarker(
                corrId, WorkflowLogEntry.TYPE_IMPORT,
                userId, "alice", "app1", "data1", "file.csv",
                now, 1234L);

        assertThat(e.correlationId()).isEqualTo(corrId);
        assertThat(e.workflowType()).isEqualTo(WorkflowLogEntry.TYPE_IMPORT);
        assertThat(e.userId()).isEqualTo(userId);
        assertThat(e.userLogin()).isEqualTo("alice");
        assertThat(e.applicationName()).isEqualTo("app1");
        assertThat(e.dataType()).isEqualTo("data1");
        assertThat(e.resourceName()).isEqualTo("file.csv");
        assertThat(e.startTime()).isEqualTo(now);
        assertThat(e.status()).isEqualTo(WorkflowLogEntry.STATUS_IN_PROGRESS);
        assertThat(e.bytesTotal()).isEqualTo(1234L);

        // Champs terminaux ( a remplir plus tard via UPSERT ) :
        assertThat(e.endTime()).isNull();
        assertThat(e.duration()).isNull();
        assertThat(e.recordsProcessed()).isZero();
        assertThat(e.recordsFailed()).isZero();
        assertThat(e.chunksProcessed()).isZero();
        assertThat(e.fatalError()).isNull();
        assertThat(e.metadata()).isNull();
        assertThat(e.errors()).isEmpty();
    }

    @Test
    @DisplayName("STATUS_IN_PROGRESS expose la constante utilisee cote SQL ( record_workflow_start )")
    void status_in_progress_constant_is_stable() {
        // Verifie que la constante Java reste alignee avec le default SQL
        // ( cf V4__workflow_log_pre_persist.sql : status = 'IN_PROGRESS' ) .
        assertThat(WorkflowLogEntry.STATUS_IN_PROGRESS).isEqualTo("IN_PROGRESS");
    }
}
