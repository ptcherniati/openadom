package fr.inra.oresing.workflow.phase;

import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single source of truth for publishing workflow progress counters
 * ( {@code recordsTotal} + {@code finalRowsWritten} ) to
 * {@link WorkflowActiveRegistry} so the oa-live progress bar can render
 * a determinate percentage at every stage of the workflow lifecycle .
 *
 * <h2>Why this abstraction</h2>
 *
 * <p>Before consolidation the progress counters were updated ad-hoc :
 * cascade workers emitted incremental {@code addFinalRows} during
 * UPSERT loops , but non-cascade workflows ( unpublish , delete-file )
 * pushed only {@code recordsTotal} via the pre-count and never marked
 * the work as completed . Result : oa-live displayed a perpetual 0 %
 * progress on these atomic workflows even after successful completion ,
 * because {@code finalRowsWritten} stayed at 0 .
 *
 * <p>This component encapsulates the 3 symmetric reporting calls every
 * workflow must perform :
 * <ol>
 *   <li>{@link #reportTotal} - announce expected work at start ;
 *       UI bar switches from indeterminate to determinate .</li>
 *   <li>{@link #reportProgress} - incremental updates during long ops
 *       ( cascade UPSERT batches , chunked DELETE , ... ) .</li>
 *   <li>{@link #reportCompleted} - declare 100 % done with the
 *       authoritative final count ; required for atomic ops that
 *       cannot emit progress mid-flight ( single SQL DELETE on
 *       1M+ rows ) .</li>
 * </ol>
 *
 * <h2>Cid resolution</h2>
 *
 * <p>All progress counters are stored at the dashboard cid ( child
 * cascade cid for PUBLISH , parent cid for UNPUBLISH / DELETE_FILE ) -
 * mirrors {@link WorkflowPhaseTracker} resolution . Callers pass the
 * parent correlationId ; this component resolves the dashboard cid
 * via the {@link PublishLifecycleCoordinator} .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class WorkflowProgressReporter {

    private final WorkflowActiveRegistry          registry;
    private final PublishLifecycleCoordinator     coordinator;

    public WorkflowProgressReporter(WorkflowActiveRegistry registry,
                                    PublishLifecycleCoordinator coordinator) {
        this.registry    = registry;
        this.coordinator = coordinator;
    }

    /**
     * Announces the total expected work upfront . Switches the UI
     * progress bar from indeterminate to determinate immediately .
     * No-op if any argument is null or invalid .
     */
    public void reportTotal(UUID parentCorrelationId, long total) {
        if (parentCorrelationId == null || total < 0) return;
        try {
            registry.setRecordsTotal(resolveCid(parentCorrelationId), total);
        } catch (RuntimeException ex) {
            log.warn("progressReporter : reportTotal failed for cid={} total={} : {}",
                    parentCorrelationId, total, ex.getMessage());
        }
    }

    /**
     * Reports cumulative work done so far ( progressively ) for
     * long-running operations that can emit incremental progress
     * ( cascade UPSERT batches , chunked DELETE , ... ) .
     */
    public void reportProgress(UUID parentCorrelationId, long processedSoFar) {
        if (parentCorrelationId == null || processedSoFar < 0) return;
        try {
            registry.setFinalRowsAuthoritative(resolveCid(parentCorrelationId), processedSoFar);
        } catch (RuntimeException ex) {
            log.warn("progressReporter : reportProgress failed for cid={} count={} : {}",
                    parentCorrelationId, processedSoFar, ex.getMessage());
        }
    }

    /**
     * Declares the workflow done at 100 % with the authoritative final
     * count . Required for atomic operations that cannot emit progress
     * mid-flight ( single SQL DELETE , single SQL UPSERT ) so the UI
     * does not stay stuck at 0 % after a successful completion .
     *
     * <p>Sets BOTH the expected total ( in case it was not declared
     * earlier ) AND the progress counter to the same value , forcing
     * the displayed percentage to 100 % .
     */
    public void reportCompleted(UUID parentCorrelationId, long finalCount) {
        if (parentCorrelationId == null || finalCount < 0) return;
        reportTotal(parentCorrelationId, finalCount);
        reportProgress(parentCorrelationId, finalCount);
    }

    private UUID resolveCid(UUID parentCorrelationId) {
        if (coordinator == null) {
            return parentCorrelationId;
        }
        return coordinator.getChildImport(parentCorrelationId).orElse(parentCorrelationId);
    }
}
