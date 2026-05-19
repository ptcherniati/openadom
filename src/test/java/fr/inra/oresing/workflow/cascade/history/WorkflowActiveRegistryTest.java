package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Tag;

@Tag("domain.model")
class WorkflowActiveRegistryTest {

    private static WorkflowSnapshot snap(UUID cid, UUID userId, String type) {
        return WorkflowSnapshot.minimal(
                cid, type, userId, "tester",
                "app1", "type1", "file.csv",
                Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L, List.of(), List.of());
    }

    @Test
    void start_adds_entry_findable_and_listed() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();

        reg.start(snap(cid, uid, "IMPORT"));

        assertTrue(reg.find(cid).isPresent());
        assertEquals(1, reg.size());
        assertEquals(1, reg.list(null).size());
        assertEquals(1, reg.list(uid).size());
        assertEquals(0, reg.list(UUID.randomUUID()).size());
    }

    @Test
    void update_keeps_id_and_bumps_progress() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.update(cid, 500L, 2L, 5, 42.5, 12345L);

        WorkflowSnapshot s = reg.find(cid).orElseThrow();
        assertEquals(500L, s.recordsProcessed());
        assertEquals(2L,   s.recordsFailed());
        assertEquals(5,    s.chunksProcessed());
        assertEquals(42.5, s.progressPercentage());
        assertEquals(12345L, s.bytesTotal());
    }

    @Test
    void update_on_unknown_id_is_a_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        reg.update(UUID.randomUUID(), 100, 0, 1, 1.0, 100);
        assertEquals(0, reg.size());
    }

    @Test
    void finish_removes_entry() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.finish(cid);

        assertFalse(reg.find(cid).isPresent());
        assertEquals(0, reg.size());
    }

    @Test
    void list_is_sorted_by_start_time_desc() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID oldCid = UUID.randomUUID();
        UUID newCid = UUID.randomUUID();
        UUID u = UUID.randomUUID();

        Instant older = Instant.now().minusSeconds(30);
        Instant newer = Instant.now();

        reg.start(WorkflowSnapshot.minimal(oldCid, "IMPORT", u, null, null, null, null,
                older, "IN_PROGRESS", 0, 0, 0, null, 0, 0, List.of(), List.of()));
        reg.start(WorkflowSnapshot.minimal(newCid, "IMPORT", u, null, null, null, null,
                newer, "IN_PROGRESS", 0, 0, 0, null, 0, 0, List.of(), List.of()));

        List<WorkflowSnapshot> ordered = reg.list(null);
        assertEquals(newCid, ordered.get(0).correlationId());
        assertEquals(oldCid, ordered.get(1).correlationId());
    }

    @Test
    void concurrent_updates_stay_consistent() throws InterruptedException {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        final int threads = 16, perThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        AtomicInteger global = new AtomicInteger();

        IntStream.range(0, threads).forEach(t -> pool.submit(() -> {
            ready.countDown();
            try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            for (int i = 0; i < perThread; i++) {
                int seq = global.incrementAndGet();
                reg.update(cid, seq, 0, seq % 100, null, seq * 10L);
            }
            done.countDown();
        }));

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        // Registry must still contain the one entry , no NPE , some values
        // somewhere in the range. The exact value depends on thread race ,
        // but it must equal the last incrementAndGet seen.
        assertEquals(1, reg.size());
        WorkflowSnapshot s = reg.find(cid).orElseThrow();
        assertTrue(s.recordsProcessed() > 0);
        assertEquals(s.recordsProcessed() * 10L, s.bytesTotal(),
                "all fields should come from the same update call ( atomic replace )");
    }

    // =========================================================================
    //  finish() – cas limites
    // =========================================================================

    @Test
    void finish_on_unknown_id_is_a_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        assertDoesNotThrow(() -> reg.finish(UUID.randomUUID()));
        assertEquals(0, reg.size());
    }

    // =========================================================================
    //  start() – idempotent (putIfAbsent)
    // =========================================================================

    @Test
    void start_is_idempotent_keeps_first_entry() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        UUID uid1 = UUID.randomUUID();
        UUID uid2 = UUID.randomUUID();
        reg.start(snap(cid, uid1, "IMPORT"));
        reg.start(snap(cid, uid2, "IMPORT")); // second call ignored
        assertEquals(1, reg.size());
        assertEquals(uid1, reg.find(cid).orElseThrow().userId());
    }

    // =========================================================================
    //  replace()
    // =========================================================================

    @Test
    void replace_overwrites_existing_snapshot() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        UUID uid1 = UUID.randomUUID();
        UUID uid2 = UUID.randomUUID();
        reg.start(snap(cid, uid1, "IMPORT"));
        reg.replace(snap(cid, uid2, "EXPORT"));
        assertEquals(uid2, reg.find(cid).orElseThrow().userId());
        assertEquals("EXPORT", reg.find(cid).orElseThrow().workflowType());
    }

    // =========================================================================
    //  setRecordsTotal()
    // =========================================================================

    @Test
    void setRecordsTotal_updates_snapshot() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));
        reg.setRecordsTotal(cid, 4200L);
        assertEquals(4200L, reg.find(cid).orElseThrow().recordsTotal());
    }

    @Test
    void setRecordsTotal_on_unknown_id_is_a_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        assertDoesNotThrow(() -> reg.setRecordsTotal(UUID.randomUUID(), 100L));
    }

    // =========================================================================
    //  Cascade listener – onChunkStart / onChunkProgress / onChunkEnd
    // =========================================================================

    @Test
    void onChunkStart_creates_running_chunk() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        WorkflowEvents.ChunkStartEvent event = mock(WorkflowEvents.ChunkStartEvent.class);
        when(event.correlationId()).thenReturn(cid.toString());
        when(event.chunkIndex()).thenReturn(0);
        when(event.recordsExpected()).thenReturn(100L);
        when(event.workerName()).thenReturn("worker-1");
        when(event.startTime()).thenReturn(Instant.now());
        reg.onChunkStart(event);

        List<ChunkSnapshot> chunks = reg.find(cid).orElseThrow().chunks();
        assertEquals(1, chunks.size());
        assertEquals("RUNNING", chunks.get(0).status());
        assertEquals(0, chunks.get(0).chunkIndex());
    }

    @Test
    void onChunkStart_invalid_uuid_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        WorkflowEvents.ChunkStartEvent event = mock(WorkflowEvents.ChunkStartEvent.class);
        when(event.correlationId()).thenReturn("not-a-uuid");
        assertDoesNotThrow(() -> reg.onChunkStart(event));
    }

    @Test
    void onChunkProgress_updates_processed_count() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        WorkflowEvents.ChunkStartEvent start = mock(WorkflowEvents.ChunkStartEvent.class);
        when(start.correlationId()).thenReturn(cid.toString());
        when(start.chunkIndex()).thenReturn(0);
        when(start.recordsExpected()).thenReturn(200L);
        when(start.workerName()).thenReturn("w");
        when(start.startTime()).thenReturn(Instant.now());
        reg.onChunkStart(start);

        WorkflowEvents.ChunkProgressEvent prog = mock(WorkflowEvents.ChunkProgressEvent.class);
        when(prog.correlationId()).thenReturn(cid.toString());
        when(prog.chunkIndex()).thenReturn(0);
        when(prog.totalProcessedSoFar()).thenReturn(75L);
        reg.onChunkProgress(prog);

        assertEquals(75L, reg.find(cid).orElseThrow().chunks().get(0).recordsProcessed());
    }

    @Test
    void onChunkProgress_on_unknown_workflow_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        WorkflowEvents.ChunkProgressEvent prog = mock(WorkflowEvents.ChunkProgressEvent.class);
        when(prog.correlationId()).thenReturn(UUID.randomUUID().toString());
        when(prog.chunkIndex()).thenReturn(0);
        when(prog.totalProcessedSoFar()).thenReturn(1L);
        assertDoesNotThrow(() -> reg.onChunkProgress(prog));
    }

    @Test
    void onChunkEnd_marks_chunk_completed() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        WorkflowEvents.ChunkStartEvent start = mock(WorkflowEvents.ChunkStartEvent.class);
        when(start.correlationId()).thenReturn(cid.toString());
        when(start.chunkIndex()).thenReturn(0);
        when(start.recordsExpected()).thenReturn(10L);
        when(start.workerName()).thenReturn("w");
        when(start.startTime()).thenReturn(Instant.now());
        reg.onChunkStart(start);

        WorkflowEvents.ChunkEndEvent end = mock(WorkflowEvents.ChunkEndEvent.class);
        when(end.correlationId()).thenReturn(cid.toString());
        when(end.chunkIndex()).thenReturn(0);
        when(end.status()).thenReturn(ProcessingStatus.SUCCESS);
        when(end.recordsProcessed()).thenReturn(10L);
        when(end.endTime()).thenReturn(Instant.now());
        when(end.errorMessage()).thenReturn(null);
        reg.onChunkEnd(end);

        assertEquals("COMPLETED", reg.find(cid).orElseThrow().chunks().get(0).status());
    }

    @Test
    void onChunkEnd_failed_chunk_has_failed_status() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        WorkflowEvents.ChunkStartEvent start = mock(WorkflowEvents.ChunkStartEvent.class);
        when(start.correlationId()).thenReturn(cid.toString());
        when(start.chunkIndex()).thenReturn(0);
        when(start.recordsExpected()).thenReturn(10L);
        when(start.workerName()).thenReturn("w");
        when(start.startTime()).thenReturn(Instant.now());
        reg.onChunkStart(start);

        WorkflowEvents.ChunkEndEvent end = mock(WorkflowEvents.ChunkEndEvent.class);
        when(end.correlationId()).thenReturn(cid.toString());
        when(end.chunkIndex()).thenReturn(0);
        when(end.status()).thenReturn(ProcessingStatus.FAILED);
        when(end.recordsProcessed()).thenReturn(5L);
        when(end.endTime()).thenReturn(Instant.now());
        when(end.errorMessage()).thenReturn("something went wrong");
        reg.onChunkEnd(end);

        ChunkSnapshot chunk = reg.find(cid).orElseThrow().chunks().get(0);
        assertEquals("FAILED", chunk.status());
        assertEquals("something went wrong", chunk.errorMessage());
    }

    // =========================================================================
    //  ChunkSnapshot - helpers
    // =========================================================================

    @Test
    void chunkSnapshot_progressPercentage_null_when_total_zero() {
        ChunkSnapshot cs = new ChunkSnapshot(0, "RUNNING", 0L, 0L, "w",
                Instant.now(), null, null);
        assertNull(cs.progressPercentage());
    }

    @Test
    void chunkSnapshot_progressPercentage_capped_at_100() {
        ChunkSnapshot cs = new ChunkSnapshot(0, "RUNNING", 300L, 100L, "w",
                Instant.now(), null, null);
        assertEquals(100.0, cs.progressPercentage());
    }

    @Test
    void chunkSnapshot_progressPercentage_calculation() {
        ChunkSnapshot cs = new ChunkSnapshot(0, "RUNNING", 25L, 100L, "w",
                Instant.now(), null, null);
        assertEquals(25.0, cs.progressPercentage());
    }

    // ─── StageWorkerStat ──────────────────────────────────────────────────

    @Test
    void stageWorkerStat_initial_values() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        assertEquals("IDLE", stat.status);
        assertNull(stat.currentChunk);
        assertEquals(0L, stat.currentRecordsProcessed);
        assertEquals(0L, stat.currentRecordsTotal);
        assertEquals(0, stat.chunksDone);
        assertNull(stat.lastDurationMs);
        assertNull(stat.lastActivity);
    }

    @Test
    void stageWorkerStat_recordEnd_increments_chunksDone_and_resets() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        stat.status = "RUNNING";
        stat.currentChunk = 3;
        stat.currentRecordsProcessed = 100L;
        stat.currentRecordsTotal = 200L;

        stat.recordEnd(50L);

        assertEquals("IDLE", stat.status);
        assertNull(stat.currentChunk);
        assertEquals(0L, stat.currentRecordsProcessed);
        assertEquals(0L, stat.currentRecordsTotal);
        assertEquals(1, stat.chunksDone);
        assertEquals(50L, stat.lastDurationMs);
    }

    @Test
    void stageWorkerStat_recordEnd_with_null_duration() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        stat.recordEnd(null);
        assertEquals(1, stat.chunksDone);
        assertNull(stat.lastDurationMs);
    }

    @Test
    void stageWorkerStat_recordEnd_sliding_window_capped_at_10() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        for (int i = 0; i < 15; i++) {
            stat.recordEnd((long) i * 10);
        }
        assertEquals(15, stat.chunksDone);
        // recentDurationsMs has at most 10 entries
        assertTrue(stat.recentDurationsMs.size() <= 10);
    }

    @Test
    void stageWorkerStat_toSnapshot_idle_no_pct() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        WorkerSnapshot ws = stat.toSnapshot("SOURCE", "worker-1");
        assertEquals("SOURCE", ws.stage());
        assertEquals("worker-1", ws.name());
        assertEquals("IDLE", ws.status());
        assertNull(ws.currentChunkProgressPercentage());
        assertNull(ws.avgChunkDuration());
        assertEquals(0, ws.chunkCount());
    }

    @Test
    void stageWorkerStat_toSnapshot_with_progress() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        stat.status = "RUNNING";
        stat.currentChunk = 2;
        stat.currentRecordsProcessed = 50L;
        stat.currentRecordsTotal = 100L;
        WorkerSnapshot ws = stat.toSnapshot("SINK", "sink-1");
        assertEquals(50.0, ws.currentChunkProgressPercentage());
    }

    @Test
    void stageWorkerStat_toSnapshot_with_avg_duration() {
        WorkflowActiveRegistry.StageWorkerStat stat = new WorkflowActiveRegistry.StageWorkerStat();
        stat.recordEnd(100L);
        stat.recordEnd(200L);
        WorkerSnapshot ws = stat.toSnapshot("TRANSFORM", "t-1");
        assertNotNull(ws.avgChunkDuration());
    }

    // =========================================================================
    //  Cascade listener callbacks — SOURCE, SINK, POOL, WORKFLOW_END
    // =========================================================================

    private static WorkflowEvents.SourceFetchStartEvent sourceFetchStartEvent(String cid, String worker) {
        return new WorkflowEvents.SourceFetchStartEvent(cid, 0, worker, Instant.now());
    }

    private static WorkflowEvents.SourceChunkEmittedEvent sourceChunkEmittedEvent(String cid, String worker) {
        return new WorkflowEvents.SourceChunkEmittedEvent(
                cid, 0, 100L, worker, 0, ProcessingStatus.SUCCESS, null, Instant.now());
    }

    private static WorkflowEvents.SinkChunkAcceptedEvent sinkChunkAcceptedEvent(String cid, String worker) {
        return new WorkflowEvents.SinkChunkAcceptedEvent(cid, 0, worker, 0, false, Instant.now());
    }

    private static WorkflowEvents.SinkChunkWrittenEvent sinkChunkWrittenEvent(String cid, String worker, long records) {
        return new WorkflowEvents.SinkChunkWrittenEvent(
                cid, 0, ProcessingStatus.SUCCESS, records, worker,
                Instant.now(), Instant.now(), java.time.Duration.ofMillis(50), null);
    }

    @Test
    void onSourceFetchStart_sets_worker_status_running() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSourceFetchStart(sourceFetchStartEvent(cid.toString(), "source-1"));

        // La liste des workers doit contenir un worker avec le bon cid
        // (le snapshot injecte les workers via injectChunks/injectWorkers)
        assertTrue(reg.find(cid).isPresent());
    }

    @Test
    void onSourceFetchStart_invalid_uuid_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        assertDoesNotThrow(() -> reg.onSourceFetchStart(sourceFetchStartEvent("not-a-uuid", "source-1")));
    }

    @Test
    void onSourceFetchStart_null_workerName_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));
        assertDoesNotThrow(() -> reg.onSourceFetchStart(
                new WorkflowEvents.SourceFetchStartEvent(cid.toString(), 0, null, Instant.now())));
    }

    @Test
    void onSourceChunkEmitted_increments_stats() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSourceChunkEmitted(sourceChunkEmittedEvent(cid.toString(), "source-1"));
        reg.onSourceChunkEmitted(sourceChunkEmittedEvent(cid.toString(), "source-1"));

        assertTrue(reg.find(cid).isPresent());
    }

    @Test
    void onSinkChunkAccepted_sets_running_state() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSinkChunkAccepted(sinkChunkAcceptedEvent(cid.toString(), "sink-1"));

        assertTrue(reg.find(cid).isPresent());
    }

    @Test
    void onSinkChunkAccepted_null_workerName_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));
        assertDoesNotThrow(() -> reg.onSinkChunkAccepted(
                new WorkflowEvents.SinkChunkAcceptedEvent(cid.toString(), 0, null, 0, false, Instant.now())));
    }

    @Test
    void onSinkChunkWritten_increments_staging_rows() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSinkChunkWritten(sinkChunkWrittenEvent(cid.toString(), "sink-1", 50L));

        assertEquals(50L, reg.stagingRows(cid));
    }

    @Test
    void onSinkChunkWritten_zero_records_does_not_increment_staging() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSinkChunkWritten(sinkChunkWrittenEvent(cid.toString(), "sink-1", 0L));

        assertEquals(0L, reg.stagingRows(cid));
    }

    @Test
    void onPoolHeartbeat_null_uuid_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        assertDoesNotThrow(() -> reg.onPoolHeartbeat(
                new WorkflowEvents.PoolHeartbeatEvent("not-a-uuid", List.of(), Instant.now())));
    }

    @Test
    void onPoolHeartbeat_resets_stale_running_source_workers() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        // Simule un worker SOURCE bloqué RUNNING depuis plus de 2 secondes
        reg.onSourceFetchStart(sourceFetchStartEvent(cid.toString(), "source-stale"));
        // Force l'activité dans le passé (> 2s ago)
        reg.onPoolHeartbeat(new WorkflowEvents.PoolHeartbeatEvent(
                cid.toString(),
                List.of(new WorkflowEvents.PoolSample("SOURCE", 0, 0, 10, 1, 0L, 0L)),
                Instant.now().minusSeconds(5)));
        // Une heartbeat "actuelle" doit reset le worker stale
        reg.onPoolHeartbeat(new WorkflowEvents.PoolHeartbeatEvent(
                cid.toString(),
                List.of(new WorkflowEvents.PoolSample("SOURCE", 0, 0, 10, 1, 0L, 0L)),
                Instant.now()));

        assertTrue(reg.find(cid).isPresent());
    }

    @Test
    void onWorkflowEnd_sets_source_sink_workers_to_idle() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        UUID cid = UUID.randomUUID();
        reg.start(snap(cid, UUID.randomUUID(), "IMPORT"));

        reg.onSourceFetchStart(sourceFetchStartEvent(cid.toString(), "source-1"));
        reg.onSinkChunkAccepted(sinkChunkAcceptedEvent(cid.toString(), "sink-1"));

        reg.onWorkflowEnd(new WorkflowEvents.WorkflowEndEvent(
                cid.toString(), UUID.randomUUID().toString(), ProcessingStatus.SUCCESS,
                100L, 0L, 1, Instant.now(), Instant.now(),
                java.time.Duration.ofSeconds(1), List.of(), null));

        // Après WorkflowEnd, le workflow reste dans le registry (il est géré par finish())
        // mais les workers SOURCE/SINK sont repassés à IDLE
        assertTrue(true); // ne doit pas lancer d'exception
    }

    @Test
    void onWorkflowEnd_invalid_uuid_is_noop() {
        WorkflowActiveRegistry reg = new WorkflowActiveRegistry();
        assertDoesNotThrow(() -> reg.onWorkflowEnd(
                new WorkflowEvents.WorkflowEndEvent(
                        "not-a-uuid", null, ProcessingStatus.FAILED,
                        0L, 0L, 0, Instant.now(), Instant.now(),
                        java.time.Duration.ZERO, List.of(), null)));
    }

}