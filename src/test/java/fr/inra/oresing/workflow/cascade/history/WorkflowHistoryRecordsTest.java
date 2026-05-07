package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs des records d'historique workflow.
 * Aucun contexte Spring, aucune base de données.
 */
@Tag("domain.model")
class WorkflowHistoryRecordsTest {

    // ------------------------------------------------------------------ //
    //  ChunkSnapshot                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ChunkSnapshot")
    class ChunkSnapshotTest {

        private ChunkSnapshot running(int index, long processed, long total) {
            return new ChunkSnapshot(index, "RUNNING", processed, total,
                    "worker-1", Instant.now(), null, null);
        }

        @Test
        void progressPercentageKnownTotal() {
            ChunkSnapshot cs = running(0, 50, 100);
            assertThat(cs.progressPercentage()).isEqualTo(50.0);
        }

        @Test
        void progressPercentageUnknownTotal() {
            ChunkSnapshot cs = running(0, 50, 0);
            assertThat(cs.progressPercentage()).isNull();
        }

        @Test
        void progressPercentageCappedAt100() {
            ChunkSnapshot cs = running(0, 200, 100);
            assertThat(cs.progressPercentage()).isEqualTo(100.0);
        }

        @Test
        void withProgressUpdatesRecordsProcessed() {
            ChunkSnapshot original = running(1, 10, 100);
            ChunkSnapshot updated = original.withProgress(75);
            assertThat(updated.recordsProcessed()).isEqualTo(75);
            assertThat(updated.chunkIndex()).isEqualTo(1);
            assertThat(updated.status()).isEqualTo("RUNNING");
        }

        @Test
        void withEndUpdatesStatusAndTime() {
            Instant end = Instant.now();
            ChunkSnapshot cs = running(2, 10, 100);
            ChunkSnapshot ended = cs.withEnd("COMPLETED", 100, end, null);
            assertThat(ended.status()).isEqualTo("COMPLETED");
            assertThat(ended.recordsProcessed()).isEqualTo(100);
            assertThat(ended.endTime()).isEqualTo(end);
            assertThat(ended.errorMessage()).isNull();
        }

        @Test
        void withEndFailedCarriesErrorMessage() {
            ChunkSnapshot cs = running(3, 5, 100);
            ChunkSnapshot failed = cs.withEnd("FAILED", 5, Instant.now(), "parse error");
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.errorMessage()).isEqualTo("parse error");
        }
    }

    // ------------------------------------------------------------------ //
    //  WorkflowSnapshot                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("WorkflowSnapshot")
    class WorkflowSnapshotTest {

        private WorkflowSnapshot snapshot() {
            return WorkflowSnapshot.minimal(
                    UUID.randomUUID(), "IMPORT", UUID.randomUUID(), "user1",
                    "myapp", "taxon", "data.csv",
                    Instant.now().minusSeconds(10),
                    "PROCESSING",
                    500, 2, 5, 0.5, 1024L, 1000L,
                    List.of(), List.of());
        }

        @Test
        void elapsedMillisIsPositive() {
            WorkflowSnapshot s = snapshot();
            long elapsed = s.elapsedMillis(Instant.now());
            assertThat(elapsed).isGreaterThanOrEqualTo(0L);
        }

        @Test
        void withProgressUpdatesCounters() {
            WorkflowSnapshot s = snapshot();
            WorkflowSnapshot updated = s.withProgress(800, 5, 8, 0.8, 2048L);
            assertThat(updated.recordsProcessed()).isEqualTo(800);
            assertThat(updated.recordsFailed()).isEqualTo(5);
            assertThat(updated.chunksProcessed()).isEqualTo(8);
            assertThat(updated.progressPercentage()).isEqualTo(0.8);
            assertThat(updated.bytesTotal()).isEqualTo(2048L);
            // champs stables inchangés
            assertThat(updated.correlationId()).isEqualTo(s.correlationId());
            assertThat(updated.applicationName()).isEqualTo("myapp");
        }

        @Test
        void withRecordsTotalUpdatesField() {
            WorkflowSnapshot s = snapshot();
            WorkflowSnapshot updated = s.withRecordsTotal(5000L);
            assertThat(updated.recordsTotal()).isEqualTo(5000L);
            assertThat(updated.status()).isEqualTo(s.status());
        }

        @Test
        void withChunksReplacesChunksList() {
            WorkflowSnapshot s = snapshot();
            ChunkSnapshot c = new ChunkSnapshot(0, "RUNNING", 10, 100,
                    "w", Instant.now(), null, null);
            WorkflowSnapshot updated = s.withChunks(List.of(c));
            assertThat(updated.chunks()).hasSize(1);
            assertThat(updated.chunks().get(0).chunkIndex()).isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------ //
    //  WorkflowLogEntry — constantes et accesseurs                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("WorkflowLogEntry")
    class WorkflowLogEntryTest {

        @Test
        void typeConstants() {
            assertThat(WorkflowLogEntry.TYPE_IMPORT).isEqualTo("IMPORT");
            assertThat(WorkflowLogEntry.TYPE_EXTRACT_ZIP).isEqualTo("EXTRACT_ZIP");
            assertThat(WorkflowLogEntry.TYPE_EXTRACT_CSV).isEqualTo("EXTRACT_CSV");
            assertThat(WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES).isNotBlank();
            assertThat(WorkflowLogEntry.TYPE_EXTRACT_CHARTE).isNotBlank();
        }

        @Test
        void statusConstants() {
            assertThat(WorkflowLogEntry.STATUS_COMPLETED).isEqualTo("COMPLETED");
            assertThat(WorkflowLogEntry.STATUS_FAILED).isEqualTo("FAILED");
            assertThat(WorkflowLogEntry.STATUS_CANCELLED).isEqualTo("CANCELLED");
            assertThat(WorkflowLogEntry.STATUS_RATE_LIMITED).isEqualTo("RATE_LIMITED");
        }

        @Test
        void inProgressPhaseConstants() {
            assertThat(WorkflowLogEntry.STATUS_UPLOADING).isEqualTo("UPLOADING");
            assertThat(WorkflowLogEntry.STATUS_CHUNKING).isEqualTo("CHUNKING");
            assertThat(WorkflowLogEntry.STATUS_PROCESSING).isEqualTo("PROCESSING");
            assertThat(WorkflowLogEntry.STATUS_LOADING_DB).isEqualTo("LOADING_DB");
        }

        @Test
        void recordAccessors() {
            UUID corrId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant start = Instant.now().minusSeconds(5);
            Instant end = Instant.now();
            Duration dur = Duration.ofSeconds(5);

            WorkflowLogEntry entry = new WorkflowLogEntry(
                    corrId, "IMPORT", userId, "alice",
                    "app1", "taxon", "file.csv",
                    start, end, dur,
                    "COMPLETED", 1000L, 3L, 10, 4096L,
                    List.of("warn1"), null);

            assertThat(entry.correlationId()).isEqualTo(corrId);
            assertThat(entry.userId()).isEqualTo(userId);
            assertThat(entry.userLogin()).isEqualTo("alice");
            assertThat(entry.workflowType()).isEqualTo("IMPORT");
            assertThat(entry.applicationName()).isEqualTo("app1");
            assertThat(entry.dataType()).isEqualTo("taxon");
            assertThat(entry.recordsProcessed()).isEqualTo(1000L);
            assertThat(entry.recordsFailed()).isEqualTo(3L);
            assertThat(entry.chunksProcessed()).isEqualTo(10);
            assertThat(entry.errors()).containsExactly("warn1");
            assertThat(entry.fatalError()).isNull();
            assertThat(entry.duration()).isEqualTo(dur);
        }
    }

    // ------------------------------------------------------------------ //
    //  DashboardWorkflowDTO — factory fromSnapshot                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DashboardWorkflowDTO")
    class DashboardWorkflowDTOTest {

        @Test
        void fromSnapshotMapsAllFields() {
            UUID corrId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Instant start = Instant.now().minusSeconds(60);
            WorkflowSnapshot s = WorkflowSnapshot.minimal(
                    corrId, "IMPORT", userId, "bob",
                    "myapp", "site", "sites.csv",
                    start, "PROCESSING",
                    200, 1, 2, 0.2, 512L, 1000L,
                    List.of("e1"), List.of());

            fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO dto =
                    fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.fromSnapshot(s);

            assertThat(dto.correlationId()).isEqualTo(corrId);
            assertThat(dto.userId()).isEqualTo(userId);
            assertThat(dto.userLogin()).isEqualTo("bob");
            assertThat(dto.applicationName()).isEqualTo("myapp");
            assertThat(dto.dataType()).isEqualTo("site");
            assertThat(dto.status()).isEqualTo("PROCESSING");
            assertThat(dto.recordsProcessed()).isEqualTo(200);
            assertThat(dto.recordsTotal()).isEqualTo(1000L);
            assertThat(dto.endTime()).isNull(); // toujours null depuis snapshot
            assertThat(dto.durationMs()).isNull();
            assertThat(dto.chunks()).isEmpty();
        }

        @Test
        void fromSnapshotWithChunks() {
            ChunkSnapshot c = new ChunkSnapshot(0, "COMPLETED", 100, 100,
                    "w0", Instant.now().minusSeconds(5), Instant.now(), null);
            WorkflowSnapshot s = WorkflowSnapshot.minimal(
                    UUID.randomUUID(), "IMPORT", UUID.randomUUID(), "u",
                    "a", "t", "f.csv", Instant.now().minusSeconds(10),
                    "LOADING_DB", 100, 0, 1, 1.0, 0L, 100L,
                    List.of(), List.of(c));

            fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO dto =
                    fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.fromSnapshot(s);

            assertThat(dto.chunks()).hasSize(1);
            assertThat(dto.chunks().get(0).chunkIndex()).isEqualTo(0);
            assertThat(dto.chunks().get(0).status()).isEqualTo("COMPLETED");
        }

        @Test
        void pageRecord() {
            fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.Page page =
                    new fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.Page(
                            List.of(), 42L, 10, 0);
            assertThat(page.total()).isEqualTo(42L);
            assertThat(page.limit()).isEqualTo(10);
            assertThat(page.offset()).isEqualTo(0);
            assertThat(page.items()).isEmpty();
        }

        @Test
        void detailRecord() {
            fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.Detail d =
                    new fr.inra.oresing.rest.dashboard.DashboardWorkflowDTO.Detail(
                            null, "fatal", List.of("e1"), java.util.Map.of("k", "v"));
            assertThat(d.fatalError()).isEqualTo("fatal");
            assertThat(d.errors()).containsExactly("e1");
            assertThat(d.metadata()).containsEntry("k", "v");
        }
    }
}