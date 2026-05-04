package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.api.defaults.db.staging.DeferredFinalize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bridges a cascade {@link DeferredFinalize} handle to the lifecycle of the
 * caller's Spring {@code @Transactional} unit-of-work .
 *
 * <p>Phase B / cascade 3.0.0 : when an import runs inside an outer Spring
 * transaction, the heavy staging -> final-table UPSERT cannot run on the
 * cascade sink-thread without deadlocking the caller thread that already
 * holds row-locks on the final table . We tell the sink to capture its
 * finalize SQL as a {@link DeferredFinalize} ; the captured handle is
 * registered here on
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager}
 * so that :
 *
 * <ul>
 *   <li>{@link #afterCommit} fires the deferred SQL on a fresh connection
 *       ( the outer tx has released its row-locks ) , then invokes
 *       {@code onSuccess} so the pipeline emits {@code STATUS_COMPLETED}
 *       only once data is actually visible in DB . On failure invokes
 *       {@code onPostCommitFailure} so the pipeline emits
 *       {@code STATUS_FAILED} ( asymmetric : Spring committed but UPSERT
 *       failed - compensation_log + sweeper handle replay ) .</li>
 *   <li>{@link #afterCompletion} on rollback / unknown invokes the
 *       sink's cleanup ( DROP staging rows ) and {@code onTxRolledBack}
 *       so the pipeline removes the workflow from its active registry
 *       ( the pipeline's catch block already emitted markRollback +
 *       FAILED log ) .</li>
 * </ul>
 *
 * <p>Idempotent : {@link DeferredFinalize#execute()} and
 * {@link DeferredFinalize#cleanup()} are documented idempotent in the
 * cascade 3.0.0 contract .
 */
@Slf4j
public final class TxAwareDeferredRunner implements TransactionSynchronization {

    private final DeferredFinalize       deferred;
    private final UUID                   correlationId;
    private final Runnable               onSuccess;
    private final Consumer<Throwable>    onPostCommitFailure;
    private final Runnable               onTxRolledBack;

    public TxAwareDeferredRunner(DeferredFinalize deferred,
                                 UUID correlationId,
                                 Runnable onSuccess,
                                 Consumer<Throwable> onPostCommitFailure,
                                 Runnable onTxRolledBack) {
        this.deferred            = deferred;
        this.correlationId       = correlationId;
        this.onSuccess           = onSuccess;
        this.onPostCommitFailure = onPostCommitFailure;
        this.onTxRolledBack      = onTxRolledBack;
    }

    @Override
    public void afterCommit() {
        // Spring guarantees the outer tx has been committed when this fires .
        // Run the deferred UPSERT on a fresh connection from the pool ; the
        // default DeferredFinalize implementation opens its own connection
        // via the sink's DataSource so we are not bound to the caller's
        // ( now closed ) tx connection . Cleanup is then run inline to
        // delete staging rows tagged with this workflow ( no-op for
        // PER_CONNECTION_TEMP ; required for SHARED_UNLOGGED ) .
        try {
            deferred.execute();
            log.info("[{}] Deferred finalize executed in afterCommit", correlationId);
        } catch (SQLException | RuntimeException e) {
            // afterCommit failures are NOT propagated to the caller by Spring
            // ( the outer tx is already committed ) . Hand the failure to the
            // pipeline so it emits STATUS_FAILED + metrics + activeRegistry.finish .
            log.error("[{}] Deferred finalize FAILED in afterCommit ; staging rows "
                    + "remain , compensation flow will retry : {}",
                    correlationId, e.getMessage(), e);
            safeAccept(onPostCommitFailure, e);
            return;
        }
        try {
            deferred.cleanup();
        } catch (SQLException | RuntimeException e) {
            log.warn("[{}] Deferred staging cleanup failed after success "
                    + "( orphan sweeper will retry ) : {}",
                    correlationId, e.getMessage());
        }
        safeRun(onSuccess);
    }

    @Override
    public void afterCompletion(int status) {
        // Cleanup runs only on rollback / unknown ; success path is already
        // handled by afterCommit above .
        if (status == STATUS_COMMITTED) {
            return;
        }
        try {
            deferred.cleanup();
            log.info("[{}] Deferred staging cleanup done after rollback ( status={} )",
                    correlationId, status);
        } catch (SQLException | RuntimeException e) {
            log.warn("[{}] Deferred cleanup failed after rollback ( sweeper will retry ) : {}",
                    correlationId, e.getMessage());
        }
        safeRun(onTxRolledBack);
    }

    private void safeRun(Runnable r) {
        if (r == null) return;
        try { r.run(); }
        catch (RuntimeException e) {
            log.warn("[{}] Deferred finalize callback failed : {}", correlationId, e.getMessage());
        }
    }

    private void safeAccept(Consumer<Throwable> c, Throwable t) {
        if (c == null) return;
        try { c.accept(t); }
        catch (RuntimeException e) {
            log.warn("[{}] Deferred finalize failure-callback failed : {}", correlationId, e.getMessage());
        }
    }
}
