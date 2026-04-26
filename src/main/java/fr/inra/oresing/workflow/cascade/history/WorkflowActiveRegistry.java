package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.core.monitoring.WorkflowMonitoringService;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
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

    /** Removes the entry from the registry once the workflow is over. */
    public void finish(UUID correlationId) {
        WorkflowSnapshot removed = byCorrelationId.remove(correlationId);
        chunksByCorrelationId.remove(correlationId);
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
     * tracked via cascade listeners. Called at every read so the chunks
     * field is always fresh.
     */
    private WorkflowSnapshot injectChunks(WorkflowSnapshot s) {
        ConcurrentMap<Integer, ChunkSnapshot> chunks = chunksByCorrelationId.get(s.correlationId());
        if (chunks == null || chunks.isEmpty()) {
            return s.withChunks(List.of());
        }
        List<ChunkSnapshot> sorted = chunks.values().stream()
                .sorted(Comparator.comparingInt(ChunkSnapshot::chunkIndex))
                .toList();
        return s.withChunks(sorted);
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

    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
