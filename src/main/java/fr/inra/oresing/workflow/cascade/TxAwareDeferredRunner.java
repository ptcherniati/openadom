package fr.inra.oresing.workflow.cascade;

import fr.inrae.ore.cascade.api.defaults.db.staging.DeferredFinalize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Bridges a cascade {@link DeferredFinalize} handle to the lifecycle of the
 * caller's Spring {@code @Transactional} unit of work .
 *
 * <p>Phase B / cascade 3.0.0 : when an import runs inside an outer Spring
 * transaction ( typically the openADOM controller's
 * {@code @Transactional} ) , the heavy staging -> final-table UPSERT cannot
 * run on the cascade sink-thread without deadlocking the caller thread that
 * already holds row-locks on the final table . We tell the sink to capture
 * its finalize SQL as a {@link DeferredFinalize} ; the captured handle is
 * registered here on
 * {@link org.springframework.transaction.support.TransactionSynchronizationManager}
 * so that :
 *
 * <ul>
 *   <li>{@link #afterCommit()} fires the deferred SQL on the caller's
 *       freshly-committed connection ( same thread , same row-locks
 *       already released ) , giving the user data immediate visibility
 *       on the HTTP response and removing the dead-lock window .</li>
 *   <li>{@link #afterCompletion(int)} releases staging artifacts ( DROP /
 *       DELETE staging rows ) on rollback so we never leak orphan staging
 *       state - the {@code WorkflowZombieSweeper} fallback still applies
 *       if the JVM crashes between commit and finalize . The success path
 *       runs cleanup automatically inside {@code execute()} of the
 *       default deferred implementation , so we only invoke it on the
 *       rollback / unknown branches .</li>
 * </ul>
 *
 * <p>Idempotent : {@link DeferredFinalize#execute()} and
 * {@link DeferredFinalize#cleanup()} are documented idempotent in the
 * cascade 3.0.0 contract ; if a synchronization callback is invoked twice
 * by a buggy tx manager the second call is a no-op .
 */
@Slf4j
public final class TxAwareDeferredRunner implements TransactionSynchronization {

    private final DeferredFinalize deferred;
    private final UUID             correlationId;

    public TxAwareDeferredRunner(DeferredFinalize deferred, UUID correlationId) {
        this.deferred      = deferred;
        this.correlationId = correlationId;
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
            // ( the outer tx is already committed ) . We log loud + rely on
            // the compensation_log + WorkflowZombieSweeper to detect the
            // asymmetric state ( commit done / finalize failed ) and replay .
            log.error("[{}] Deferred finalize FAILED in afterCommit ; staging rows "
                    + "remain , compensation flow will retry : {}",
                    correlationId, e.getMessage(), e);
            return;
        }
        try {
            deferred.cleanup();
        } catch (SQLException | RuntimeException e) {
            log.warn("[{}] Deferred staging cleanup failed after success "
                    + "( orphan sweeper will retry ) : {}",
                    correlationId, e.getMessage());
        }
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
    }
}
