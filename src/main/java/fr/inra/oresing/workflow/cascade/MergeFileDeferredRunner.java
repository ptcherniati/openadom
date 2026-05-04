package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * MERGE_FILE counterpart of {@link TxAwareDeferredRunner} : when an import
 * runs inside an outer Spring tx, {@link StoreAllPathSink} is configured
 * with {@code deferToCaller=true} . The sink no longer invokes
 * {@link DataRepository#storeAll} on the cascade sink-thread (which would
 * deadlock against the caller's row-locks on the final table) ; it
 * captures the merged path . This runner :
 *
 * <ul>
 *   <li>{@link #afterCommit} : runs storeAll(merged.csv) on the caller's
 *       connection , reports the rowcount back to the sink , deletes
 *       processedDir , then invokes {@code onSuccess} so the pipeline
 *       emits STATUS_COMPLETED once data is actually visible . If
 *       storeAll fails invokes {@code onPostCommitFailure} so the
 *       pipeline emits STATUS_FAILED ( merged.csv left for replay ) .</li>
 *   <li>{@link #afterCompletion} on rollback : skips storeAll, cleans
 *       processedDir, invokes {@code onTxRolledBack} so the pipeline
 *       finalizes the active registry .</li>
 * </ul>
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
    private final Runnable               onSuccess;
    private final Consumer<Throwable>    onPostCommitFailure;
    private final Runnable               onTxRolledBack;

    public MergeFileDeferredRunner(DataRepository repository,
                                   WorkflowTempCleanup tempCleanup,
                                   StoreAllPathSink sink,
                                   Path mergedPath,
                                   Path processedDir,
                                   UUID correlationId,
                                   WorkflowActiveRegistry registry,
                                   Runnable onSuccess,
                                   Consumer<Throwable> onPostCommitFailure,
                                   Runnable onTxRolledBack) {
        this.repository          = repository;
        this.tempCleanup         = tempCleanup;
        this.sink                = sink;
        this.mergedPath          = mergedPath;
        this.processedDir        = processedDir;
        this.correlationId       = correlationId;
        this.registry            = registry;
        this.onSuccess           = onSuccess;
        this.onPostCommitFailure = onPostCommitFailure;
        this.onTxRolledBack      = onTxRolledBack;
    }

    @Override
    public void afterCommit() {
        java.util.function.LongConsumer onBatch = (registry != null && correlationId != null)
                ? n -> registry.addFinalRows(correlationId, n)
                : n -> { };
        Consumer<String> onPhase = (registry != null && correlationId != null)
                ? phase -> registry.setMergeFilePhase(correlationId, phase)
                : phase -> { };
        long upserted;
        try {
            upserted = repository.storeAll(mergedPath, onBatch, onPhase);
            sink.recordDeferredRowsWritten(upserted);
            log.info("[{}] Deferred MERGE_FILE storeAll executed in afterCommit : upserted={}",
                    correlationId, upserted);
        } catch (RuntimeException e) {
            // afterCommit failures are NOT propagated to the caller by Spring
            // ( the outer tx is already committed ) . Hand the failure to the
            // pipeline so it emits STATUS_FAILED + metrics + activeRegistry.finish .
            // The merged file stays in place for manual replay .
            log.error("[{}] Deferred MERGE_FILE storeAll FAILED in afterCommit ; "
                    + "merged.csv left for manual replay : {}",
                    correlationId, e.getMessage(), e);
            safeAccept(onPostCommitFailure, e);
            return;
        }
        tempCleanup.cleanup(processedDir);
        safeRun(onSuccess);
    }

    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_COMMITTED) {
            return;
        }
        // Rollback / unknown : never run storeAll ; just clean up the
        // merged file directory + finalize the registry .
        tempCleanup.cleanup(processedDir);
        log.info("[{}] Outer tx rolled back ( status={} ) : MERGE_FILE storeAll skipped , "
                + "processedDir cleaned",
                correlationId, status);
        safeRun(onTxRolledBack);
    }

    private void safeRun(Runnable r) {
        if (r == null) return;
        try { r.run(); }
        catch (RuntimeException e) {
            log.warn("[{}] MERGE_FILE deferred callback failed : {}", correlationId, e.getMessage());
        }
    }

    private void safeAccept(Consumer<Throwable> c, Throwable t) {
        if (c == null) return;
        try { c.accept(t); }
        catch (RuntimeException e) {
            log.warn("[{}] MERGE_FILE deferred failure-callback failed : {}", correlationId, e.getMessage());
        }
    }
}
