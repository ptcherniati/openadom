package fr.inra.oresing.workflow.cascade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry des {@code pg_backend_pid()} actifs par {@code correlationId} pour
 * permettre l'annulation reelle d'une requete SQL longue ( UPSERT staging ->
 * referencevalue , buildSynthesis , etc ) via {@code pg_cancel_backend(pid)} .
 *
 * <p><b>Pourquoi</b> : sans ce registry , un appel
 * {@link fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus#cancel} se contente
 * de positionner un flag observe par les chunks ; mais la phase finale UPSERT
 * staging -> referencevalue execute un {@code executeUpdate()} JDBC bloquant
 * cote Postgres et n'a aucun mecanisme de polling . Resultat : le workflow_log
 * est marque CANCELLED mais le SQL continue jusqu'au bout , inserant les rows
 * comme si rien ne s'etait passe . Incoherence visible pour l'utilisateur .
 *
 * <p><b>Mecanisme</b> :
 * <ol>
 *   <li>{@link fr.inra.oresing.workflow.cascade.StagingFinalizeSql#runFinalize}
 *       (et autres consumers SQL longs) appelle {@link #register} en debut de
 *       phase apres {@code SELECT pg_backend_pid()} sur la connection courante .</li>
 *   <li>{@link fr.inra.oresing.rest.dashboard.DashboardService#cancelWorkflow}
 *       look up le pid via {@link #findPid} et envoie {@code pg_cancel_backend(pid)}
 *       depuis une connection separee . La requete UPSERT en cours leve
 *       {@code PSQLException : canceling statement due to user request} ,
 *       remontee en SinkException -> rollback transaction sticky -> rows
 *       non commitees -> coherence preservee .</li>
 *   <li>Le {@code finally} du consumer SQL appelle {@link #deregister} pour
 *       eviter la fuite memoire long-terme .</li>
 * </ol>
 *
 * <p><b>Trade-off</b> : pas d'atomicite stricte mais best-effort tres
 * efficace dans la plupart des cas ( fenetre etroite entre register et la
 * fin du statement ) . Si cancel arrive apres deregister , l'operation
 * est consideree completee et le workflow_log refletera son resultat reel .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendPidRegistry {

    private final ConcurrentMap<UUID, Integer> pidByCorrelationId = new ConcurrentHashMap<>();

    /**
     * Set des correlationIds qui ont recu un signal cancel AVANT d'avoir
     * registered leur pg_backend_pid . Sans ce set , le cancel signal envoye
     * par DashboardService.cancelWorkflow se perd ( findPid empty , no-op )
     * si le workflow n'a pas encore atteint son register . Le sub se register
     * apres ( register call ) , detecte le pre-cancel et abort immediatement
     * via {@link CancelledBeforeRegistrationException} -> caller catch -> finalize skip .
     */
    private final java.util.Set<UUID> preCancelledCids = ConcurrentHashMap.newKeySet();

    /**
     * Enregistre le {@code pg_backend_pid()} de la connection executant un
     * SQL long pour ce workflow . Idempotent : si une entree existe deja
     * pour ce correlationId , elle est ecrasee ( cas usage : 2 phases SQL
     * sequentielles sur la meme correlationId ) .
     */
    public void register(UUID correlationId, int pid) {
        if (correlationId == null || pid <= 0) return;
        // Race protection : si un cancel signal a deja ete envoye AVANT cette
        // register ( typique : sub-IMPORT cascade lance par publish , user cancel
        // pendant transform phase , sub atteint finalize plus tard ) , on abort
        // ICI plutot que de proceder a un UPSERT sur la table terminale .
        // Garantie : aucune row commitee en target table apres un cancel observe .
        if (preCancelledCids.contains(correlationId)) {
            log.info("Workflow {} pre-cancelled : abort register + signal pour caller", correlationId);
            throw new CancelledBeforeRegistrationException(correlationId);
        }
        pidByCorrelationId.put(correlationId, pid);
        log.debug("Backend pid {} registered for workflow {}", pid, correlationId);
    }

    /** Exception levee si une register arrive apres qu'un cancel ait deja signale ce cid . */
    public static class CancelledBeforeRegistrationException extends RuntimeException {
        public CancelledBeforeRegistrationException(UUID correlationId) {
            super("Workflow " + correlationId + " was cancelled before pid registration ; finalize abort");
        }
    }

    /**
     * Retire l'entree du registry . A appeler en finally apres la fin du
     * SQL long ( succes ou echec ) . Idempotent .
     */
    public void deregister(UUID correlationId) {
        if (correlationId == null) return;
        Integer removed = pidByCorrelationId.remove(correlationId);
        preCancelledCids.remove(correlationId);  // cleanup tracking
        if (removed != null) {
            log.debug("Backend pid {} deregistered for workflow {}", removed, correlationId);
        }
    }

    /**
     * Recupere le pid enregistre pour un workflow , empty si aucun SQL long
     * n'est en cours pour ce correlationId .
     */
    public Optional<Integer> findPid(UUID correlationId) {
        if (correlationId == null) return Optional.empty();
        return Optional.ofNullable(pidByCorrelationId.get(correlationId));
    }

    /**
     * Boilerplate helper : query {@code SELECT pg_backend_pid()} on the current
     * JDBC connection , {@link #register} it under {@code correlationId} , run
     * {@code work} , and always {@link #deregister} in finally . Returns the
     * Runnable result so callers can keep using lambdas inside transactions .
     *
     * <p>Eliminates the 5+ copies of the same try/finally block scattered
     * across {@code PublishLifecyclePhase2Handler} ,
     * {@code PublishFastPathDirectExecutor} : a single source of truth for the
     * register-pid-then-run-then-deregister contract that the SQL cancel
     * mechanism depends on .
     *
     * @param jdbcTemplate JDBC template bound to the same tx-scoped connection
     *                     ( typically a {@code new JdbcTemplate(dataSource)} so
     *                     {@code DataSourceUtils} returns the active conn ) .
     * @param correlationId workflow id under which to register the pid .
     * @param work          long-running SQL block to execute under registration .
     */
    public void runWithRegistration(JdbcTemplate jdbcTemplate, UUID correlationId, Runnable work) {
        Integer pid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
        if (pid != null && pid > 0) {
            register(correlationId, pid);
        }
        try {
            work.run();
        } finally {
            deregister(correlationId);
        }
    }

    /**
     * Envoie {@code pg_cancel_backend(pid)} depuis une connection separee .
     * Le statement SQL en cours sur la connection ciblee leve une SQLException
     * " canceling statement due to user request " . Spring rollback la tx
     * sticky -> rows non commitees -> etat coherent .
     *
     * @return true si la fonction PG a retourne true ( signal envoye avec succes )
     */
    public boolean cancelBackend(JdbcTemplate jdbcTemplate, UUID correlationId) {
        if (correlationId == null) return false;
        // Mark pre-cancelled : si le pid n'est pas encore registered , la prochaine
        // register lèvera CancelledBeforeRegistrationException -> finalize abort -> 0 commit terminale .
        preCancelledCids.add(correlationId);
        return findPid(correlationId).map(pid -> {
            try {
                Boolean ok = jdbcTemplate.queryForObject(
                        "SELECT pg_cancel_backend(?)", Boolean.class, pid);
                boolean cancelled = Boolean.TRUE.equals(ok);
                log.info("pg_cancel_backend({}) for workflow {} : success={}",
                        pid, correlationId, cancelled);
                return cancelled;
            } catch (RuntimeException ex) {
                log.warn("pg_cancel_backend({}) for workflow {} failed : {}",
                        pid, correlationId, ex.getMessage());
                return false;
            }
        }).orElse(false);
    }
}
