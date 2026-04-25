package fr.inra.oresing.workflow.cascade.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
 * {@link #finish(UUID)} once the workflow completes. Nothing persists across
 * a JVM restart - a backend restart is treated as "all workflows lost" from
 * the dashboard's point of view.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
@Service
@Slf4j
public class WorkflowActiveRegistry {

    private final ConcurrentMap<UUID, WorkflowSnapshot> byCorrelationId = new ConcurrentHashMap<>();

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
        if (removed != null) {
            log.debug("Workflow unregistered : {} / {}",
                    removed.workflowType(), correlationId);
        }
    }

    /** Direct lookup , used by the detail endpoint to serve live data. */
    public Optional<WorkflowSnapshot> find(UUID correlationId) {
        return Optional.ofNullable(byCorrelationId.get(correlationId));
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
                .collect(Collectors.toList());
    }

    /** Number of entries currently tracked. Mostly useful for tests + health. */
    public int size() {
        return byCorrelationId.size();
    }
}
