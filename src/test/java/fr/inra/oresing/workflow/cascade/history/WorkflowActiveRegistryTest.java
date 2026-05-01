package fr.inra.oresing.workflow.cascade.history;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowActiveRegistryTest {

    private static WorkflowSnapshot snap(UUID cid, UUID userId, String type) {
        return new WorkflowSnapshot(
                cid, type, userId, "tester",
                "app1", "type1", "file.csv",
                Instant.now(), "IN_PROGRESS",
                0L, 0L, 0, null, 0L, 0L, List.of(), List.of(), List.of(), null, null);
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

        reg.start(new WorkflowSnapshot(oldCid, "IMPORT", u, null, null, null, null,
                older, "IN_PROGRESS", 0, 0, 0, null, 0, 0, List.of(), List.of(), List.of(), null, null));
        reg.start(new WorkflowSnapshot(newCid, "IMPORT", u, null, null, null, null,
                newer, "IN_PROGRESS", 0, 0, 0, null, 0, 0, List.of(), List.of(), List.of(), null, null));

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
}
