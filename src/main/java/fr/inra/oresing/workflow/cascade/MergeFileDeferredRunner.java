package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Bridges a captured MERGE_FILE {@code merged.csv} path to the lifecycle of
 * the caller's Spring {@code @Transactional} unit-of-work .
 *
 * <p>Phase B / cascade 3.0.0 ( MERGE_FILE counterpart of
 * {@link TxAwareDeferredRunner} for DIRECT_COPY ) : when an import runs
 * inside an outer Spring tx , {@link StoreAllPathSink} is configured with
 * {@code deferToCaller=true} . The sink no longer invokes
 * {@link DataRepository#storeAll} on the cascade sink-thread ( which would
 * deadlock against the caller's row-locks on the final table ) ; it
 * captures the merged path instead and exposes it via
 * {@link StoreAllPathSink#takeDeferredMergedPath()} . This runner is
 * registered on
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager}
 * by {@code CascadeImportPipeline} and :
 *
 * <ul>
 *   <li>{@link #afterCommit} : runs {@code storeAll(merged.csv)} on the
 *       caller's connection ( fresh from the pool ; the outer tx has
 *       already released its row-locks ) , reports the rowcount back to
 *       the sink so {@code workflow_log.records_processed} stays
 *       accurate , then deletes the {@code processedDir} that holds the
 *       merged file .</li>
 *   <li>{@link #afterCompletion} when {@code !STATUS_COMMITTED} : the
 *       outer tx rolled back ; we never run {@code storeAll} ( consistent
 *       with the user's intent ) and only delete {@code processedDir} so
 *       we do not leak temp files .</li>
 * </ul>
 *
 * <p>Failure of {@code storeAll} in {@code afterCommit} cannot propagate
 * to the caller ( the outer tx is already committed ) ; we log loudly and
 * leave the merged file in place so the user can replay manually . The
 * compensation_log + zombie sweeper detect the asymmetric state .
 */
@Slf4j
public final class MergeFileDeferredRunner implements TransactionSynchronization {

    private final DataRepository         repository;
    private final WorkflowTempCleanup    tempCleanup;
    private final StoreAllPathSink       sink;
    private final Path                   mergedPath;
    private final Path                   processedDir;
    private final UUID                   correlationId;
    private final WorkflowActiveRegistry registry;

    public MergeFileDeferredRunner(DataRepository repository,
                                   WorkflowTempCleanup tempCleanup,
                                   StoreAllPathSink sink,
                                   Path mergedPath,
                                   Path processedDir,
                                   UUID correlationId,
                                   WorkflowActiveRegistry registry) {
        this.repository    = repository;
        this.tempCleanup   = tempCleanup;
        this.sink          = sink;
        this.mergedPath    = mergedPath;
        this.processedDir  = processedDir;
        this.correlationId = correlationId;
        this.registry      = registry;
    }

    @Override
    public void afterCommit() {
        LongConsumer onBatch = (registry != null && correlationId != null)
                ? n -> registry.addFinalRows(correlationId, n)
                : n -> { };
        Consumer<String> onPhase = (registry != null && correlationId != null)
                ? phase -> registry.setMergeFilePhase(correlationId, phase)
                : phase -> { };
        try {
            long upserted = repository.storeAll(mergedPath, onBatch, onPhase);
            sink.recordDeferredRowsWritten(upserted);
            log.info("[{}] Deferred MERGE_FILE storeAll executed in afterCommit : upserted={}",
                    correlationId, upserted);
        } catch (RuntimeException e) {
            // afterCommit failures are NOT propagated to the caller by Spring
            // ( the outer tx is already committed ) . The merged file stays
            // in place for manual replay ; compensation_log + zombie sweeper
            // detect the asymmetric state ( commit done / storeAll failed ) .
            log.error("[{}] Deferred MERGE_FILE storeAll FAILED in afterCommit ; "
                    + "merged.csv left for manual replay : {}",
                    correlationId, e.getMessage(), e);
            return;
        }
        tempCleanup.cleanup(processedDir);
    }

    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) {
            return;
        }
        // Rollback / unknown : never run storeAll ; just clean up the
        // merged file directory so we do not leak temp files .
        tempCleanup.cleanup(processedDir);
        log.info("[{}] Outer tx rolled back ( status={} ) : MERGE_FILE storeAll skipped , "
                + "processedDir cleaned",
                correlationId, status);
    }
}
