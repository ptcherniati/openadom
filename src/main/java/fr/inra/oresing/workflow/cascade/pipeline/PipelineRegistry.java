package fr.inra.oresing.workflow.cascade.pipeline;

import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * In-memory registry of per-workflow pipeline state , consumed by the
 * oa-live Pipeline view ( {@code GET /api/dashboard/workflows/{id}/pipeline} ) .
 *
 * <p>Subscribes as a {@link WorkflowListener} on cascade's monitoring
 * service ; converts the 1.9.0 push events into a {@link PipelineSnapshot}
 * snapshot read by the REST endpoint . No DB persistence ; data lives
 * only as long as the workflow is active in the registry .
 *
 * <p>State per workflow :
 * <ul>
 *   <li>per-worker {@code WorkerSlot} maps , one per stage</li>
 *   <li>latest pool counters from {@link WorkflowEvents.PoolHeartbeatEvent}</li>
 *   <li>sliding window of last 30 events for flow animation</li>
 *   <li>rolling 5-second throughput counter per stage</li>
 * </ul>
 *
 * <p>Cleanup : entries are dropped on workflow end ( see
 * {@link #onWorkflowEnd} ) so memory stays bounded by the active set .
 */
@Service
@Slf4j
public class PipelineRegistry implements WorkflowListener {

    private static final int RECENT_EVENT_WINDOW = 30;
    private static final long THROUGHPUT_WINDOW_MS = 5_000L;
    private static final String STATUS_RUNNING = "RUNNING";

    private final ConcurrentMap<UUID, PipelineState> byCorrelationId = new ConcurrentHashMap<>();

    @PostConstruct
    void wire() {
        WorkflowEventBus.getInstance().subscribe(this);
        log.info("PipelineRegistry subscribed to cascade WorkflowEventBus");
    }

    @PreDestroy
    void unwire() {
        WorkflowEventBus.getInstance().unsubscribe(this);
    }

    /**
     * Returns a fresh {@link PipelineSnapshot} for the requested workflow ,
     * or empty if the workflow is no longer active .
     */
    public Optional<PipelineSnapshot> snapshot(UUID correlationId) {
        PipelineState state = byCorrelationId.get(correlationId);
        return state == null ? Optional.empty() : Optional.of(state.toSnapshot());
    }

    /** Number of active pipelines tracked ; mostly for tests / health . */
    public int size() {
        return byCorrelationId.size();
    }

    // ----------------------------------------------------------------
    //  cascade listener callbacks
    // ----------------------------------------------------------------

    @Override
    public void onWorkflowStart(WorkflowEvents.WorkflowStartEvent e) {
        UUID id = safeUuid(e.correlationId());
        if (id == null) return;
        byCorrelationId.computeIfAbsent(id, k -> new PipelineState(id));
    }

    @Override
    public void onWorkflowEnd(WorkflowEvents.WorkflowEndEvent e) {
        UUID id = safeUuid(e.correlationId());
        if (id != null) {
            byCorrelationId.remove(id);
        }
    }

    @Override
    public void onSourceTotalChunksKnown(WorkflowEvents.SourceTotalChunksKnownEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s != null) s.totalChunks = e.totalChunks();
    }

    @Override
    public void onSourceFetchStart(WorkflowEvents.SourceFetchStartEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.sourceWorker(e.workerName());
        w.status = STATUS_RUNNING;
        w.lastActivity = e.time();
    }

    @Override
    public void onSourceChunkEmitted(WorkflowEvents.SourceChunkEmittedEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.sourceWorker(e.workerName());
        w.status = "IDLE";
        w.chunksDoneTotal++;
        w.recordsTotal += e.recordsEmitted();
        w.lastActivity = e.time();
        s.pushEvent(new PipelineSnapshot.PipelineEvent(
                "SOURCE_EMIT", e.chunkIndex(), "SOURCE", "TRANSFORM_QUEUE", e.time()));
        s.sourceTput.add(e.recordsEmitted(), e.time());
    }

    @Override
    public void onChunkStart(WorkflowEvents.ChunkStartEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.transformWorker(e.workerName());
        w.status = STATUS_RUNNING;
        w.currentChunk = e.chunkIndex();
        w.currentRecordsTotal = e.recordsExpected();
        w.currentRecordsProcessed = 0;
        w.lastActivity = e.startTime();
    }

    @Override
    public void onChunkProgress(WorkflowEvents.ChunkProgressEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.transformWorker(e.workerName());
        w.currentRecordsProcessed = e.totalProcessedSoFar();
        w.lastActivity = e.time();
    }

    @Override
    public void onChunkEnd(WorkflowEvents.ChunkEndEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.transformWorker(e.workerName());
        w.status = "IDLE";
        w.chunksDoneTotal++;
        w.currentChunk = null;
        w.currentRecordsProcessed = 0;
        w.currentRecordsTotal = 0;
        w.lastDurationMs = e.duration() != null ? e.duration().toMillis() : null;
        w.recentDurationsMs.addLast(w.lastDurationMs != null ? w.lastDurationMs : 0L);
        if (w.recentDurationsMs.size() > 10) w.recentDurationsMs.removeFirst();
        w.lastActivity = e.endTime();
        s.transformTput.add(e.recordsProcessed(), e.endTime());
    }

    @Override
    public void onSinkChunkAccepted(WorkflowEvents.SinkChunkAcceptedEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.sinkWorker(e.workerName());
        w.status = STATUS_RUNNING;
        w.currentChunk = e.chunkIndex();
        w.lastActivity = e.time();
        s.pushEvent(new PipelineSnapshot.PipelineEvent(
                "SINK_ACCEPT", e.chunkIndex(), "TRANSFORM_QUEUE", "SINK", e.time()));
    }

    @Override
    public void onSinkChunkWritten(WorkflowEvents.SinkChunkWrittenEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        WorkerStat w = s.sinkWorker(e.workerName());
        w.status = "IDLE";
        w.chunksDoneTotal++;
        w.currentChunk = null;
        w.lastDurationMs = e.duration() != null ? e.duration().toMillis() : null;
        w.recentDurationsMs.addLast(w.lastDurationMs != null ? w.lastDurationMs : 0L);
        if (w.recentDurationsMs.size() > 10) w.recentDurationsMs.removeFirst();
        w.recordsTotal += e.recordsWritten();
        w.lastActivity = e.endTime();
        s.pushEvent(new PipelineSnapshot.PipelineEvent(
                "SINK_WRITTEN", e.chunkIndex(), "SINK", "DONE", e.endTime()));
        s.sinkTput.add(e.recordsWritten(), e.endTime());
    }

    @Override
    public void onPoolHeartbeat(WorkflowEvents.PoolHeartbeatEvent e) {
        PipelineState s = stateFor(e.correlationId());
        if (s == null) return;
        for (WorkflowEvents.PoolSample p : e.pools()) {
            switch (p.stage()) {
                case "SOURCE"    -> s.sourcePool    = p;
                case "TRANSFORM" -> s.transformPool = p;
                case "SINK"      -> s.sinkPool      = p;
                default          -> { /* ORDERING ignored for now */ }
            }
        }
        s.snapshotAt = e.time();

        // Stale-RUNNING reset : SOURCE has no "fetch end" event when the
        // spliterator advance returns false on EOF , so a source worker
        // can stay RUNNING long past the actual end-of-stream . Heartbeat
        // is the only periodic signal that lets us close that gap before
        // workflow end .
        long nowMs = (e.time() != null ? e.time() : Instant.now()).toEpochMilli();
        resetStaleRunning(s.sourceWorkers, nowMs);
        resetStaleRunning(s.sinkWorkers,   nowMs);
    }

    private static final long STALE_RUNNING_RESET_MS = 2_000L;

    private static void resetStaleRunning(Map<String, WorkerStat> workers, long nowMs) {
        for (WorkerStat w : workers.values()) {
            if (!STATUS_RUNNING.equals(w.status)) continue;
            if (w.lastActivity == null) continue;
            if (nowMs - w.lastActivity.toEpochMilli() > STALE_RUNNING_RESET_MS) {
                w.status = "IDLE";
                w.currentChunk = null;
                w.currentRecordsProcessed = 0L;
                w.currentRecordsTotal = 0L;
            }
        }
    }

    // ----------------------------------------------------------------
    //  helpers
    // ----------------------------------------------------------------

    private PipelineState stateFor(String correlationId) {
        UUID id = safeUuid(correlationId);
        return id != null ? byCorrelationId.get(id) : null;
    }

    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    // ================================================================
    //  Internal mutable state ( per workflow )
    // ================================================================

    /** Mutable per-workflow accumulator built from cascade push events . */
    static final class PipelineState {
        final UUID correlationId;
        volatile Instant snapshotAt = Instant.now();

        // pool stats from heartbeat
        volatile WorkflowEvents.PoolSample sourcePool;
        volatile WorkflowEvents.PoolSample transformPool;
        volatile WorkflowEvents.PoolSample sinkPool;

        volatile Integer totalChunks;

        final Map<String, WorkerStat> sourceWorkers    = new ConcurrentHashMap<>();
        final Map<String, WorkerStat> transformWorkers = new ConcurrentHashMap<>();
        final Map<String, WorkerStat> sinkWorkers      = new ConcurrentHashMap<>();

        final Deque<PipelineSnapshot.PipelineEvent> recentEvents = new ArrayDeque<>();
        final RollingThroughput sourceTput    = new RollingThroughput(THROUGHPUT_WINDOW_MS);
        final RollingThroughput transformTput = new RollingThroughput(THROUGHPUT_WINDOW_MS);
        final RollingThroughput sinkTput      = new RollingThroughput(THROUGHPUT_WINDOW_MS);

        PipelineState(UUID correlationId) { this.correlationId = correlationId; }

        WorkerStat sourceWorker(String name) {
            return sourceWorkers.computeIfAbsent(name, n -> new WorkerStat(n));
        }
        WorkerStat transformWorker(String name) {
            return transformWorkers.computeIfAbsent(name, n -> new WorkerStat(n));
        }
        WorkerStat sinkWorker(String name) {
            return sinkWorkers.computeIfAbsent(name, n -> new WorkerStat(n));
        }

        synchronized void pushEvent(PipelineSnapshot.PipelineEvent e) {
            recentEvents.addLast(e);
            while (recentEvents.size() > RECENT_EVENT_WINDOW) {
                recentEvents.removeFirst();
            }
        }

        PipelineSnapshot toSnapshot() {
            Instant now = Instant.now();
            return new PipelineSnapshot(
                    correlationId,
                    snapshotAt != null ? snapshotAt : now,
                    buildStage("SOURCE",    sourcePool,    sourceWorkers),
                    buildStage("TRANSFORM", transformPool, transformWorkers),
                    buildStage("SINK",      sinkPool,      sinkWorkers),
                    queueOf(transformPool),
                    queueOf(sinkPool),
                    totalChunks,
                    snapshotEvents(),
                    new PipelineSnapshot.Throughput(
                            sourceTput.linesPerSec(now),
                            transformTput.linesPerSec(now),
                            sinkTput.linesPerSec(now)));
        }

        private PipelineSnapshot.StagePool buildStage(String stage,
                                                     WorkflowEvents.PoolSample pool,
                                                     Map<String, WorkerStat> workers) {
            int  parallelism  = pool != null ? pool.poolSize() : workers.size();
            int  active       = pool != null ? pool.activeCount() : 0;
            long completed    = pool != null ? pool.completedTaskCount() : 0L;
            List<PipelineSnapshot.WorkerSlot> slots = workers.values().stream()
                    .sorted(Comparator.comparing(w -> w.name))
                    .map(WorkerStat::toSlot)
                    .toList();
            return new PipelineSnapshot.StagePool(stage, parallelism, active, completed, slots);
        }

        private PipelineSnapshot.QueueState queueOf(WorkflowEvents.PoolSample p) {
            if (p == null) return new PipelineSnapshot.QueueState(0, 0, 0d);
            int depth    = Math.max(0, p.queueSize());
            int capacity = Math.max(0, p.queueCapacity());
            double sat   = capacity > 0 ? (depth * 100d) / capacity : 0d;
            return new PipelineSnapshot.QueueState(depth, capacity, sat);
        }

        private synchronized List<PipelineSnapshot.PipelineEvent> snapshotEvents() {
            return new ArrayList<>(recentEvents);
        }
    }

    /** Mutable per-worker accumulator . */
    static final class WorkerStat {
        final String  name;
        volatile String  status = "IDLE";
        volatile Integer currentChunk;
        volatile long    currentRecordsProcessed;
        volatile long    currentRecordsTotal;
        volatile int     chunksDoneTotal;
        volatile long    recordsTotal;
        volatile Long    lastDurationMs;
        volatile Instant lastActivity;
        final Deque<Long> recentDurationsMs = new ArrayDeque<>();

        WorkerStat(String name) { this.name = name; }

        PipelineSnapshot.WorkerSlot toSlot() {
            Double pct = (currentRecordsTotal > 0)
                    ? Math.min(100d, (currentRecordsProcessed * 100d) / currentRecordsTotal)
                    : null;
            Long avg = recentDurationsMs.isEmpty()
                    ? null
                    : (long) recentDurationsMs.stream().mapToLong(Long::longValue).average().orElse(0d);
            return new PipelineSnapshot.WorkerSlot(
                    name, status, currentChunk,
                    currentRecordsProcessed, currentRecordsTotal,
                    pct, chunksDoneTotal, lastDurationMs, avg);
        }
    }

    /** Rolling-window throughput counter ; not thread-safe but only mutated from listener callbacks . */
    static final class RollingThroughput {
        private final long windowMs;
        private final Deque<long[]> samples = new ArrayDeque<>();   // [epochMs, records]

        RollingThroughput(long windowMs) { this.windowMs = windowMs; }

        synchronized void add(long records, Instant at) {
            if (records <= 0 || at == null) return;
            samples.addLast(new long[]{ at.toEpochMilli(), records });
            evictOlderThan(at.toEpochMilli());
        }

        synchronized double linesPerSec(Instant now) {
            evictOlderThan(now.toEpochMilli());
            if (samples.isEmpty()) return 0d;
            long total = samples.stream().mapToLong(s -> s[1]).sum();
            long oldestMs = samples.peekFirst()[0];
            long spanMs   = Math.max(1L, now.toEpochMilli() - oldestMs);
            return (total * 1000d) / spanMs;
        }

        private void evictOlderThan(long nowMs) {
            long cutoff = nowMs - windowMs;
            while (!samples.isEmpty() && samples.peekFirst()[0] < cutoff) {
                samples.removeFirst();
            }
        }
    }

    // suppress unused-import warning ( Stream + Duration not needed at runtime
    // but kept for forward refactor compatibility ) .
    @SuppressWarnings("unused")
    private static void __keepImports() {
        Stream.empty();
        Duration.ZERO.toMillis();
    }
}