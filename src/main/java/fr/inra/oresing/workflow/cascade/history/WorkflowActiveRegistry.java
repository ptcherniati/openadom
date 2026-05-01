package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.core.monitoring.WorkflowMonitoringService;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory registry of workflows currently in progress.
 *
 * <p>The oa_metrics.workflow_log table only receives rows when a workflow
 * finishes ( COMPLETED / FAILED / CANCELLED / RATE_LIMITED ). For the
 * live dashboard oa-live we need a snapshot of running workflows with their
 * latest progress values ; that snapshot lives in this thread-safe registry.
 *
 * <p>Entries are added on {@link #start(WorkflowSnapshot)} by the
 * orchestrators , updated on every progress event via
 * {@link #update(UUID, long, long, int, Double, long)} , and removed by
 * {@link #finish(UUID)} once the workflow completes.
 *
 * <p>Per-chunk drill-down ( plan E ) : the registry subscribes itself as
 * a cascade {@link WorkflowListener} on
 * {@link WorkflowMonitoringService#getDefault()} ; the
 * {@code onChunkStart / onChunkProgress / onChunkEnd} hooks update a
 * separate per-workflow {@link ChunkSnapshot} map merged at read time.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
@Service
@Slf4j
public class WorkflowActiveRegistry implements WorkflowListener {

    private final ConcurrentMap<UUID, WorkflowSnapshot> byCorrelationId = new ConcurrentHashMap<>();

    /** Per-workflow chunk state ( ConcurrentHashMap of ConcurrentHashMap ). */
    private final ConcurrentMap<UUID, ConcurrentMap<Integer, ChunkSnapshot>> chunksByCorrelationId =
            new ConcurrentHashMap<>();

    /**
     * Per-workflow per-worker SOURCE / SINK stats . Cascade 1.9.0+ emits
     * chunk events ONLY for the transform stage ; source / sink stages
     * publish dedicated lifecycle events
     * ( {@code SourceFetchStart} / {@code SourceChunkEmitted} ,
     *   {@code SinkChunkAccepted} / {@code SinkChunkWritten} ) . We
     * accumulate them into lightweight {@link StageWorkerStat} entries so
     * the {@link WorkerSnapshot} aggregation can emit one row per stage
     * worker - and the oa-live Workers view shows the full pipeline ,
     * not only transform .
     */
    private final ConcurrentMap<UUID, ConcurrentMap<String, StageWorkerStat>> sourceWorkersByCid =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, StageWorkerStat>> sinkWorkersByCid =
            new ConcurrentHashMap<>();

    /**
     * Subscribes this registry as a cascade listener at startup so that
     * onChunkStart / onChunkProgress / onChunkEnd events feed the
     * per-chunk drill-down.
     */
    @PostConstruct
    void wireCascadeListener() {
        WorkflowMonitoringService.getDefault().subscribe(this);
        log.info("WorkflowActiveRegistry subscribed to cascade WorkflowMonitoringService");
    }

    @PreDestroy
    void unwireCascadeListener() {
        WorkflowMonitoringService.getDefault().unsubscribe(this);
    }

    // ----------------------------------------------------------------
    //  Workflow-level state ( unchanged API )
    // ----------------------------------------------------------------

    /** Registers a workflow as started. Does nothing if already present. */
    public void start(WorkflowSnapshot snapshot) {
        byCorrelationId.putIfAbsent(snapshot.correlationId(), snapshot);
        log.debug("Workflow registered : {} / {}", snapshot.workflowType(), snapshot.correlationId());
    }

    /**
     * Updates progress on an existing entry. No-op if the correlationId
     * is not registered ( late update after finish , or racing ). Safe to
     * call concurrently from multiple workers.
     */
    public void update(
            UUID correlationId,
            long recordsProcessed,
            long recordsFailed,
            int chunksProcessed,
            Double progressPercentage,
            long bytesTotal) {

        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withProgress(
                        recordsProcessed, recordsFailed, chunksProcessed,
                        progressPercentage, bytesTotal));
    }

    /**
     * Records the total number of records expected for a workflow once
     * known ( typically after the file has been counted ). Allows oa-live
     * to switch the progress bar from indeterminate to determinate.
     * No-op if the entry is not registered.
     */
    public void setRecordsTotal(UUID correlationId, long recordsTotal) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withRecordsTotal(recordsTotal));
    }

    /**
     * Records the resolved parallelism block ( source / transform / sink )
     * for a workflow once the executor has decided which thread pools to
     * use . Called by {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline}
     * right after the {@link fr.inrae.ore.cascade.model.workflow.WorkflowConfig}
     * is finalised . No-op if the entry is not registered .
     */
    public void setParallelism(UUID correlationId, ParallelismSnapshot parallelism) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withParallelism(parallelism));
    }

    /**
     * Records the resolved cascade strategy ( sinkStrategy ,
     * stagingStrategy , executionMode , streamingMode ,
     * directWriteParallel ) for a workflow . Called by
     * {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline}
     * right after the {@link fr.inrae.ore.cascade.model.workflow.WorkflowConfig}
     * is finalised . No-op if the entry is not registered .
     */
    public void setStrategy(UUID correlationId, StrategySnapshot strategy) {
        byCorrelationId.computeIfPresent(correlationId, (id, cur) ->
                cur.withStrategy(strategy));
    }

    /** Removes the entry from the registry once the workflow is over. */
    public void finish(UUID correlationId) {
        WorkflowSnapshot removed = byCorrelationId.remove(correlationId);
        chunksByCorrelationId.remove(correlationId);
        sourceWorkersByCid.remove(correlationId);
        sinkWorkersByCid.remove(correlationId);
        if (removed != null) {
            log.debug("Workflow unregistered : {} / {}",
                    removed.workflowType(), correlationId);
        }
    }

    /**
     * Replaces the workflow snapshot in-place ( phase update ) without
     * touching the chunks map. Useful for transitions like
     * UPLOADING -> CHUNKING -> PROCESSING -> LOADING_DB where the
     * caller wants to keep the live chunks data.
     */
    public void replace(WorkflowSnapshot snapshot) {
        byCorrelationId.put(snapshot.correlationId(), snapshot);
    }

    // ----------------------------------------------------------------
    //  Lookup ( injects chunks at read time )
    // ----------------------------------------------------------------

    /** Direct lookup , used by the detail endpoint to serve live data. */
    public Optional<WorkflowSnapshot> find(UUID correlationId) {
        WorkflowSnapshot s = byCorrelationId.get(correlationId);
        return Optional.ofNullable(s).map(this::injectChunks);
    }

    /**
     * Returns a snapshot of all active workflows , sorted by startTime DESC
     * ( most recent first ). Optionally filtered by user id - null means no
     * filter ( admin view ).
     */
    public List<WorkflowSnapshot> list(UUID userFilter) {
        Collection<WorkflowSnapshot> all = byCorrelationId.values();
        return all.stream()
                .filter(s -> userFilter == null || userFilter.equals(s.userId()))
                .sorted(Comparator.comparing(WorkflowSnapshot::startTime).reversed())
                .map(this::injectChunks)
                .collect(Collectors.toList());
    }

    /** Number of entries currently tracked. Mostly useful for tests + health. */
    public int size() {
        return byCorrelationId.size();
    }

    /**
     * Replaces {@code WorkflowSnapshot.chunks} with the live chunk state
     * tracked via cascade listeners . Called at every read so the chunks
     * field is always fresh . Also computes the per-worker aggregated view
     * ( {@link WorkerSnapshot} ) so the dashboard does not have to group
     * client-side .
     */
    private WorkflowSnapshot injectChunks(WorkflowSnapshot s) {
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(s.correlationId());
        List<ChunkSnapshot> sortedChunks = (chunks == null || chunks.isEmpty())
                ? List.of()
                : chunks.values().stream()
                        .sorted(Comparator.comparingInt(ChunkSnapshot::chunkIndex))
                        .toList();

        // 3 stages combined into a single ordered list ( SOURCE then
        // TRANSFORM then SINK ) so the dashboard renders them in
        // pipeline-natural order .
        List<WorkerSnapshot> workers = new java.util.ArrayList<>();
        workers.addAll(stageWorkers(sourceWorkersByCid.get(s.correlationId()), "SOURCE"));
        workers.addAll(aggregateTransformWorkers(sortedChunks));
        workers.addAll(stageWorkers(sinkWorkersByCid.get(s.correlationId()),   "SINK"));

        return s.withChunks(sortedChunks).withWorkers(List.copyOf(workers));
    }

    /**
     * Aggregates {@link ChunkSnapshot} entries into one
     * {@link WorkerSnapshot} per distinct {@code workerName} for the
     * TRANSFORM stage . Renamed in 1.9.1 from {@code aggregateWorkers}
     * to avoid confusion with the new source / sink aggregations .
     */
    private static List<WorkerSnapshot> aggregateTransformWorkers(List<ChunkSnapshot> chunks) {
        Map<String, List<ChunkSnapshot>> byWorker = new LinkedHashMap<>();
        for (ChunkSnapshot c : chunks) {
            String name = c.workerName();
            if (name == null || name.isBlank()) continue;
            byWorker.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(c);
        }
        return byWorker.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> buildWorkerSnapshot(e.getKey(), e.getValue()))
                .toList();
    }

    private static WorkerSnapshot buildWorkerSnapshot(String name, List<ChunkSnapshot> entries) {
        ChunkSnapshot running = entries.stream()
                .filter(c -> "RUNNING".equals(c.status()))
                .findFirst().orElse(null);

        ChunkSnapshot lastFinished = entries.stream()
                .filter(c -> c.endTime() != null)
                .max(Comparator.comparing(ChunkSnapshot::endTime))
                .orElse(null);

        Duration lastDuration = null;
        if (lastFinished != null && lastFinished.startTime() != null) {
            lastDuration = Duration.between(lastFinished.startTime(), lastFinished.endTime());
        }

        // Rolling average over the last 10 finished chunks of this worker .
        // Smooths the per-tick jitter when chunks complete fast , while still
        // tracking long-term throughput drifts ( e.g. degradation when the
        // DB starts thrashing ) .
        List<Duration> recentDurations = entries.stream()
                .filter(c -> c.endTime() != null && c.startTime() != null)
                .sorted(Comparator.comparing(ChunkSnapshot::endTime).reversed())
                .limit(10)
                .map(c -> Duration.between(c.startTime(), c.endTime()))
                .toList();
        Duration avgDuration = null;
        if (!recentDurations.isEmpty()) {
            long avgNanos = (long) recentDurations.stream()
                    .mapToLong(Duration::toNanos)
                    .average()
                    .orElse(0d);
            avgDuration = Duration.ofNanos(avgNanos);
        }

        Instant lastActivity = entries.stream()
                .flatMap(c -> java.util.stream.Stream.of(c.startTime(), c.endTime()))
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String status        = running != null ? "RUNNING" : "IDLE";
        Integer currentChunk = running != null ? running.chunkIndex() : null;
        long curProcessed    = running != null ? running.recordsProcessed() : 0L;
        long curTotal        = running != null ? running.recordsTotal()     : 0L;
        Double curPct        = running != null ? running.progressPercentage() : null;

        return new WorkerSnapshot(
                "TRANSFORM",
                name,
                status,
                currentChunk,
                curProcessed,
                curTotal,
                curPct,
                entries.size(),
                lastDuration,
                avgDuration,
                lastActivity);
    }

    /**
     * Builds {@link WorkerSnapshot} list for SOURCE / SINK stages from
     * the lightweight {@link StageWorkerStat} accumulators . Sorted by
     * worker name so the UI grid stays stable across refreshes .
     */
    private static List<WorkerSnapshot> stageWorkers(
            ConcurrentMap<String, StageWorkerStat> stats, String stage) {
        if (stats == null || stats.isEmpty()) return List.of();
        return stats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().toSnapshot(stage, e.getKey()))
                .toList();
    }

    /**
     * Mutable per-worker accumulator for SOURCE / SINK stages . Updated
     * from listener callbacks ; converted to an immutable
     * {@link WorkerSnapshot} at read time .
     */
    static final class StageWorkerStat {
        volatile String  status = "IDLE";
        volatile Integer currentChunk;
        volatile long    currentRecordsProcessed;
        volatile long    currentRecordsTotal;
        volatile int     chunksDone;
        volatile Long    lastDurationMs;
        volatile Instant lastActivity;
        final java.util.Deque<Long> recentDurationsMs = new java.util.ArrayDeque<>();

        synchronized void recordEnd(Long durationMs) {
            chunksDone++;
            status = "IDLE";
            currentChunk = null;
            currentRecordsProcessed = 0L;
            currentRecordsTotal = 0L;
            if (durationMs != null) {
                lastDurationMs = durationMs;
                recentDurationsMs.addLast(durationMs);
                while (recentDurationsMs.size() > 10) recentDurationsMs.removeFirst();
            }
        }

        synchronized WorkerSnapshot toSnapshot(String stage, String name) {
            Long avgMs = recentDurationsMs.isEmpty()
                    ? null
                    : (long) recentDurationsMs.stream().mapToLong(Long::longValue).average().orElse(0d);
            Double pct = (currentRecordsTotal > 0)
                    ? Math.min(100d, (currentRecordsProcessed * 100d) / currentRecordsTotal)
                    : null;
            return new WorkerSnapshot(
                    stage, name, status, currentChunk,
                    currentRecordsProcessed, currentRecordsTotal, pct,
                    chunksDone,
                    lastDurationMs == null ? null : Duration.ofMillis(lastDurationMs),
                    avgMs == null ? null : Duration.ofMillis(avgMs),
                    lastActivity);
        }
    }

    // ----------------------------------------------------------------
    //  Cascade WorkflowListener implementation
    // ----------------------------------------------------------------

    @Override
    public void onChunkStart(WorkflowEvents.ChunkStartEvent e) {
        log.debug("[{}] onChunkStart : chunk #{} expects {} records on {}",
                e.correlationId(), e.chunkIndex(), e.recordsExpected(), e.workerName());
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) {
            log.warn("onChunkStart : correlationId '{}' is not a valid UUID , chunk skipped",
                    e.correlationId());
            return;
        }
        chunksByCorrelationId
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .put(e.chunkIndex(), new ChunkSnapshot(
                        e.chunkIndex(),
                        "RUNNING",
                        0L,
                        e.recordsExpected(),
                        e.workerName(),
                        e.startTime(),
                        null,
                        null));
    }

    @Override
    public void onChunkProgress(WorkflowEvents.ChunkProgressEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(corrId);
        if (chunks == null) return;
        chunks.computeIfPresent(e.chunkIndex(), (idx, cur) ->
                cur.withProgress(e.totalProcessedSoFar()));
    }

    @Override
    public void onChunkEnd(WorkflowEvents.ChunkEndEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(corrId);
        if (chunks == null) return;
        String status = switch (e.status()) {
            case SUCCESS  -> "COMPLETED";
            case FAILED   -> "FAILED";
            case CANCELLED -> "CANCELLED";
            default       -> e.status().name();
        };
        chunks.computeIfPresent(e.chunkIndex(), (idx, cur) ->
                cur.withEnd(status, e.recordsProcessed(), e.endTime(), e.errorMessage()));
    }

    // ----------------------------------------------------------------
    //  Workflow lifecycle ( cascade ) - reset stuck workers
    // ----------------------------------------------------------------

    /**
     * Resets every still-RUNNING SOURCE / SINK worker to IDLE when
     * cascade signals the workflow is over .
     *
     * <p>Background : {@code SourceInstrumentation} ( cascade 1.9.0+ )
     * fires {@code SourceFetchStartEvent} before every spliterator
     * {@code tryAdvance()} ; when the source is exhausted the
     * underlying advance returns {@code false} without invoking the
     * consumer , so the matching {@code SourceChunkEmittedEvent} is
     * never fired and the source worker stays stuck in RUNNING . This
     * handler closes the loop : at workflow end , any stale RUNNING
     * worker is forced to IDLE so the dashboard does not display a
     * "phantom" source / sink activity during the post-workflow
     * {@code CHARGEMENT_DB} phase ( e.g. {@code storeAll} on
     * MERGE_FILE ) .
     */
    @Override
    public void onWorkflowEnd(WorkflowEvents.WorkflowEndEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null) return;
        ConcurrentMap<String, StageWorkerStat> sources = sourceWorkersByCid.get(corrId);
        if (sources != null) {
            sources.values().forEach(w -> { w.status = "IDLE"; w.currentChunk = null; });
        }
        ConcurrentMap<String, StageWorkerStat> sinks = sinkWorkersByCid.get(corrId);
        if (sinks != null) {
            sinks.values().forEach(w -> { w.status = "IDLE"; w.currentChunk = null; });
        }
    }

    // ----------------------------------------------------------------
    //  SOURCE stage listener callbacks ( cascade 1.9.0 events )
    // ----------------------------------------------------------------

    @Override
    public void onSourceFetchStart(WorkflowEvents.SourceFetchStartEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sourceWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        w.status = "RUNNING";
        w.lastActivity = e.time();
    }

    @Override
    public void onSourceChunkEmitted(WorkflowEvents.SourceChunkEmittedEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sourceWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        // Source has no chunk-end timing , so we just bump the counter
        // and reset to IDLE . durationMs is unknown for the source path .
        w.recordEnd(null);
        w.lastActivity = e.time();
    }

    // ----------------------------------------------------------------
    //  SINK stage listener callbacks ( cascade 1.9.0 events )
    // ----------------------------------------------------------------

    @Override
    public void onSinkChunkAccepted(WorkflowEvents.SinkChunkAcceptedEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sinkWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        w.status = "RUNNING";
        w.currentChunk = e.chunkIndex();
        w.lastActivity = e.time();
    }

    @Override
    public void onSinkChunkWritten(WorkflowEvents.SinkChunkWrittenEvent e) {
        UUID corrId = safeUuid(e.correlationId());
        if (corrId == null || e.workerName() == null) return;
        StageWorkerStat w = sinkWorkersByCid
                .computeIfAbsent(corrId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(e.workerName(), n -> new StageWorkerStat());
        Long durMs = e.duration() != null ? e.duration().toMillis() : null;
        w.recordEnd(durMs);
        w.lastActivity = e.endTime() != null ? e.endTime() : Instant.now();
    }

    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
