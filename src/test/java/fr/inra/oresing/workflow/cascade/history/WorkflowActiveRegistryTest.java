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
    void list_is_sorted_by_start_time_desc() throws Exception {
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
}