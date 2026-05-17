package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.monitoring.session.SessionInfo;
import fr.inra.oresing.monitoring.session.UserSessionLogEntry;
import fr.inra.oresing.workflow.cascade.config.PoolReloader;
import fr.inra.oresing.workflow.cascade.pipeline.PipelineSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les DTOs dashboard restants :
 * {@link SessionDTO}, {@link FinalizeProgressDTO}, {@link FinalizeAggregateDTO},
 * {@link PipelineDTO}, {@link PipelinePoolsDTO}.
 */
@Tag("domain.model")
@DisplayName("Dashboard DTOs — SessionDTO, FinalizeProgressDTO, PipelineDTO, FinalizeAggregateDTO")
class RemainingDashboardDtosTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final Instant NOW     = Instant.now();
    private static final Instant LOGIN   = NOW.minus(Duration.ofHours(1));
    private static final Instant EXPIRES = NOW.plus(Duration.ofHours(7));

    // ─── SessionDTO ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SessionDTO.fromSession() : session active → status ACTIVE, durationMs non null")
    void sessionDtoFromSessionActive() {
        SessionInfo active = new SessionInfo(
                SESSION_ID, USER_ID, "alice", "127.0.0.1", "UA",
                LOGIN, EXPIRES, null, null, null);

        SessionDTO dto = SessionDTO.fromSession(active, NOW);

        assertThat(dto.sessionId()).isEqualTo(SESSION_ID);
        assertThat(dto.userId()).isEqualTo(USER_ID);
        assertThat(dto.userLogin()).isEqualTo("alice");
        assertThat(dto.status()).isEqualTo("ACTIVE");
        assertThat(dto.durationMs()).isNotNull().isGreaterThan(0L);
        assertThat(dto.logoutTime()).isNull();
        assertThat(dto.endReason()).isNull();
    }

    @Test
    @DisplayName("SessionDTO.fromSession() : session terminée → status DISCONNECTED")
    void sessionDtoFromSessionEnded() {
        SessionInfo ended = new SessionInfo(
                SESSION_ID, USER_ID, "alice", "127.0.0.1", "UA",
                LOGIN, EXPIRES, NOW, SessionInfo.END_LOGOUT, null);

        SessionDTO dto = SessionDTO.fromSession(ended, NOW);

        assertThat(dto.status()).isEqualTo("DISCONNECTED");
        assertThat(dto.endReason()).isEqualTo(SessionInfo.END_LOGOUT);
        assertThat(dto.logoutTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("SessionDTO.fromLogEntry() : construit depuis UserSessionLogEntry")
    void sessionDtoFromLogEntry() {
        UserSessionLogEntry entry = new UserSessionLogEntry(
                SESSION_ID, USER_ID, "alice", "127.0.0.1", "UA",
                LOGIN, NOW, Duration.ofHours(1).toMillis(), SessionInfo.END_LOGOUT);

        SessionDTO dto = SessionDTO.fromLogEntry(entry);

        assertThat(dto.sessionId()).isEqualTo(SESSION_ID);
        assertThat(dto.status()).isEqualTo("DISCONNECTED");
        assertThat(dto.durationMs()).isEqualTo(Duration.ofHours(1).toMillis());
        assertThat(dto.expiresAt()).isNull();
    }

    @Test
    @DisplayName("SessionDTO.Page : champs accessibles")
    void sessionDtoPage() {
        SessionInfo active = new SessionInfo(SESSION_ID, USER_ID, "alice", "127.0.0.1",
                "UA", LOGIN, EXPIRES, null, null, null);
        SessionDTO item = SessionDTO.fromSession(active, NOW);

        SessionDTO.Page page = new SessionDTO.Page(List.of(item), 1L, 10, 0);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.limit()).isEqualTo(10);
        assertThat(page.offset()).isZero();
        assertThat(page.items()).hasSize(1);
    }

    // ─── FinalizeProgressDTO ──────────────────────────────────────────────────

    @Test
    @DisplayName("FinalizeProgressDTO : record construit et champs accessibles")
    void finalizeProgressDto() {
        FinalizeProgressDTO dto = new FinalizeProgressDTO(
                "CASCADE_RUNNING", "MERGE_FILE", null,
                NOW, null, null, null, null, null,
                0L, 0L, 0L,
                1000L, 0L, -1L,
                500L, 0L, null,
                0L, 0L, false, false, null);

        assertThat(dto.phase()).isEqualTo("CASCADE_RUNNING");
        assertThat(dto.sinkStrategy()).isEqualTo("MERGE_FILE");
        assertThat(dto.stagingStrategy()).isNull();
        assertThat(dto.expectedTotal()).isEqualTo(1000L);
        assertThat(dto.cascadeThroughput()).isEqualTo(500L);
        assertThat(dto.mergeFilePhase()).isNull();
    }

    // ─── FinalizeAggregateDTO ─────────────────────────────────────────────────

    @Test
    @DisplayName("FinalizeAggregateDTO : record construit et champs accessibles")
    void finalizeAggregateDto() {
        FinalizeAggregateDTO dto = new FinalizeAggregateDTO(
                3, 1, 0, 2, 9000L, 7000L, 200L, 800L);

        assertThat(dto.nbActive()).isEqualTo(3);
        assertThat(dto.nbFinalize()).isEqualTo(1);
        assertThat(dto.nbRollback()).isZero();
        assertThat(dto.nbCompleted()).isEqualTo(2);
        assertThat(dto.expectedTotalSum()).isEqualTo(9000L);
        assertThat(dto.finalCountSum()).isEqualTo(7000L);
        assertThat(dto.stagingRemainingSum()).isEqualTo(200L);
        assertThat(dto.finalizeThroughputSum()).isEqualTo(800L);
    }

    @Test
    @DisplayName("FinalizeAggregateDTO : record equality")
    void finalizeAggregateDtoEquality() {
        FinalizeAggregateDTO a = new FinalizeAggregateDTO(0, 0, 0, 0, 0L, 0L, 0L, 0L);
        FinalizeAggregateDTO b = new FinalizeAggregateDTO(0, 0, 0, 0, 0L, 0L, 0L, 0L);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // ─── PipelineDTO ──────────────────────────────────────────────────────────

    private PipelineSnapshot minimalPipelineSnapshot(UUID corrId) {
        PipelineSnapshot.StagePool stage = new PipelineSnapshot.StagePool(
                "TRANSFORM", 4, 2, 50L, List.of());
        PipelineSnapshot.QueueState queue = new PipelineSnapshot.QueueState(5, 50, 10.0);
        PipelineSnapshot.Throughput tp = new PipelineSnapshot.Throughput(100.0, 90.0, 80.0);
        return new PipelineSnapshot(corrId, NOW, stage, stage, stage,
                queue, queue, 10, List.of(), tp);
    }

    @Test
    @DisplayName("PipelineDTO.from(snapshot) : champs de base corrects")
    void pipelineDtoFromSnapshot() {
        UUID corrId = UUID.randomUUID();
        PipelineDTO dto = PipelineDTO.from(minimalPipelineSnapshot(corrId));

        assertThat(dto.correlationId()).isEqualTo(corrId);
        assertThat(dto.snapshotAt()).isEqualTo(NOW);
        assertThat(dto.transform().stage()).isEqualTo("TRANSFORM");
        assertThat(dto.transform().parallelism()).isEqualTo(4);
        assertThat(dto.transform().activeCount()).isEqualTo(2);
        assertThat(dto.transform().poolCoreSize()).isEqualTo(-1); // pool null → -1
        assertThat(dto.totalChunks()).isEqualTo(10);
        assertThat(dto.recentEvents()).isEmpty();
        assertThat(dto.throughput().sourceLinesPerSec()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("PipelineDTO.from(snapshot) : QueueDTO.from() calcule saturationPct")
    void pipelineDtoQueueSaturation() {
        UUID corrId = UUID.randomUUID();
        PipelineDTO dto = PipelineDTO.from(minimalPipelineSnapshot(corrId));
        // transformInbox a depth=5, capacity=50 → 10%
        assertThat(dto.transformInbox().saturationPct()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("PipelineDTO.from(snapshot, pools) : pool non-null → poolCoreSize renseigné")
    void pipelineDtoFromSnapshotWithPool() {
        UUID corrId = UUID.randomUUID();

        PoolReloader.PoolSnapshot pool = new PoolReloader.PoolSnapshot(
                PoolReloader.Stage.TRANSFORM, 4, 4, 2, 4, 5, 50);

        PipelineDTO dto = PipelineDTO.from(minimalPipelineSnapshot(corrId), null, pool, null);

        assertThat(dto.transform().poolCoreSize()).isEqualTo(4);
        assertThat(dto.source().poolCoreSize()).isEqualTo(-1); // sourcePool=null
    }

    // ─── PipelineDTO inner records ────────────────────────────────────────────

    @Test
    @DisplayName("PipelineDTO.ThroughputDTO.from() : valeurs mappées")
    void pipelineDtoThroughput() {
        PipelineSnapshot.Throughput tp = new PipelineSnapshot.Throughput(200.0, 190.0, 180.0);
        PipelineDTO.ThroughputDTO dto = PipelineDTO.ThroughputDTO.from(tp);
        assertThat(dto.sourceLinesPerSec()).isEqualTo(200.0);
        assertThat(dto.transformLinesPerSec()).isEqualTo(190.0);
        assertThat(dto.sinkLinesPerSec()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("PipelineDTO.EventDTO.from() : valeurs mappées")
    void pipelineDtoEvent() {
        PipelineSnapshot.PipelineEvent evt = new PipelineSnapshot.PipelineEvent(
                "SINK_WRITTEN", 5, "TRANSFORM", "SINK", NOW);
        PipelineDTO.EventDTO dto = PipelineDTO.EventDTO.from(evt);
        assertThat(dto.kind()).isEqualTo("SINK_WRITTEN");
        assertThat(dto.chunkIndex()).isEqualTo(5);
        assertThat(dto.at()).isEqualTo(NOW);
    }
}
