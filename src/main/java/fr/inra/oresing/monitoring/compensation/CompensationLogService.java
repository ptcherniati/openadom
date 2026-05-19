package fr.inra.oresing.monitoring.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API publique pour le journal de compensation .
 *
 * <p>Methodes principales :
 * <ul>
 *   <li>{@link #record} : INSERT row PENDING ( a appeler avant l'op risquee )</li>
 *   <li>{@link #confirm} : DELETE row apres succes</li>
 *   <li>{@link #compensateNow} : execute le handler immediatement
 *       ( cleanup synchrone dans un finally )</li>
 *   <li>{@link #listPending} / {@link #listFailed} : pour l'UI admin</li>
 * </ul>
 *
 * <p>Toutes les operations sont en {@code REQUIRES_NEW} : elles commit
 * independamment de la tx outer , garantissant la visibilite immediate
 * de la row PENDING avant que l'op risquee demarre .
 *
 * @author R.YAHIAOUI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationLogService {

    private final CompensationLogRepository repository;
    private final CompensationHandlerRegistry handlerRegistry;

    /**
     * Insere une row PENDING dans une tx independante ( REQUIRES_NEW ) .
     * Au retour , la row est COMMITTED et visible cross-connection .
     *
     * @return l'UUID de la row inseree , a passer ensuite a {@link #confirm}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(String operationType,
                       String targetSchema, String targetTable, String targetId,
                       UUID correlationId, UUID userId, String userLogin,
                       Map<String, Object> payload,
                       int ttlMinutes) {
        UUID id = repository.recordPending(operationType,
                targetSchema, targetTable, targetId,
                correlationId, userId, userLogin, payload, ttlMinutes);
        log.debug("CompensationLog recorded : {} for {}.{}/{}", id, targetSchema, targetTable, targetId);
        return id;
    }

    /** Default TTL ( 240 min ) . */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(String operationType,
                       String targetSchema, String targetTable, String targetId,
                       UUID correlationId, UUID userId, String userLogin,
                       Map<String, Object> payload) {
        return record(operationType, targetSchema, targetTable, targetId,
                correlationId, userId, userLogin, payload,
                CompensationLogEntry.DEFAULT_TTL_MINUTES);
    }

    /**
     * DELETE row de log apres succes de l'op . En tx independante .
     * Idempotent : pas d'erreur si la row n'existe deja plus .
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean confirm(UUID id) {
        boolean deleted = repository.delete(id);
        if (deleted) log.debug("CompensationLog confirmed : {}", id);
        return deleted;
    }

    /**
     * Execute la compensation immediatement ( hors sweeper ) .
     * Use case : finally block apres echec d'une op , best-effort cleanup
     * synchrone . Si succes : row DELETE . Si fail : row reste PENDING ,
     * sweeper rattrapera apres TTL .
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean compensateNow(UUID id) {
        CompensationLogEntry entry = repository.findById(id);
        if (entry == null) {
            log.debug("compensateNow : row {} already gone , no-op", id);
            return true;
        }
        return runHandlerAndDelete(entry);
    }

    /**
     * Idem mais via handler invocation depuis le sweeper . Retourne
     * {@code true} si compensation reussie + row DELETE ; {@code false}
     * si handler a thrown ( retry ulterieur ) .
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean runHandlerAndDelete(CompensationLogEntry entry) {
        var handler = handlerRegistry.handlerFor(entry.operationType());
        if (handler.isEmpty()) {
            log.error("No CompensationHandler for operationType '{}' , skipping row {}",
                    entry.operationType(), entry.id());
            repository.recordFailure(entry.id(),
                    "No handler registered for type " + entry.operationType(),
                    CompensationLogEntry.MAX_RETRIES);
            return false;
        }
        try {
            handler.get().compensate(entry);
            repository.delete(entry.id());
            log.info("Compensated row {} ( {} ) for {}.{}/{}",
                    entry.id(), entry.operationType(),
                    entry.targetSchema(), entry.targetTable(), entry.targetId());
            return true;
        } catch (RuntimeException ex) {
            log.warn("Compensation handler {} failed for row {} : {}",
                    entry.operationType(), entry.id(), ex.getMessage());
            repository.recordFailure(entry.id(), ex.getMessage(), CompensationLogEntry.MAX_RETRIES);
            return false;
        }
    }

    /**
     * Admin "Skip" : DELETE row de log sans executer de compensation .
     * Le binaryfile / target row reste intact . Use case : admin a
     * inspecte et decide que l'op est en realite reussie ( cas confirm
     * raté ) .
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean skipAndDelete(UUID id) {
        return repository.delete(id);
    }

    public List<CompensationLogEntry> listPending(int limit) {
        return repository.findByStatus(CompensationLogEntry.STATUS_PENDING, limit);
    }

    public List<CompensationLogEntry> listFailed(int limit) {
        return repository.findByStatus(CompensationLogEntry.STATUS_FAILED, limit);
    }

    public CompensationLogEntry findById(UUID id) {
        return repository.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CompensationLogEntry> findStalePendingForSweep(int batchSize) {
        return repository.lockStalePendingBatch(batchSize);
    }

    public int deleteFailedOlderThan(int retentionDays) {
        return repository.deleteFailedOlderThan(retentionDays);
    }
}
