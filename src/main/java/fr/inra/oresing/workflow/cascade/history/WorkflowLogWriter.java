package fr.inra.oresing.workflow.cascade.history;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writer asynchrone pour {@code oa_audit.workflow_log}.
 *
 * <p>Principe :
 * <ul>
 *   <li>Les sites metier appellent {@link #logAsync(WorkflowLogEntry)} qui
 *       empile dans une queue non-bloquante et retourne immediatement.</li>
 *   <li>Un thread worker dedie poll la queue , batch-insert toutes les
 *       {@code flushIntervalMs} ms ou quand la queue atteint
 *       {@code batchSize} elements.</li>
 *   <li>Si la queue est pleine ( {@code queueCapacity} ), les nouvelles
 *       entries sont droppees ( log.warn ) pour ne jamais bloquer le
 *       thread metier.</li>
 * </ul>
 *
 * <p>Propriete cle : <b>l'observabilite ne doit jamais ralentir la
 * production</b>. Ce writer est fail-safe par design.
 *
 * <p>Phase 2 observabilite (issue #62).
 */
@Slf4j
@Service
public class WorkflowLogWriter {

    private final WorkflowLogRepository repository;
    private final int                   batchSize;
    private final long                  flushIntervalMs;

    private BlockingQueue<WorkflowLogEntry> queue;
    private ExecutorService                 executor;
    private volatile boolean                running;

    public WorkflowLogWriter(
            WorkflowLogRepository repository,
            @Value("${app.metrics.writer.queue-capacity:5000}") int queueCapacity,
            @Value("${app.metrics.writer.batch-size:50}") int batchSize,
            @Value("${app.metrics.writer.flush-interval-ms:2000}") long flushIntervalMs) {
        this.repository       = repository;
        this.queue            = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize        = batchSize;
        this.flushIntervalMs  = flushIntervalMs;
        log.info("WorkflowLogWriter config : queueCapacity={} , batchSize={} , flushIntervalMs={}",
                queueCapacity, batchSize, flushIntervalMs);
    }

    @PostConstruct
    void start() {
        this.running = true;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "workflow-log-writer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::runLoop);
        log.info("WorkflowLogWriter demarre");
    }

    /**
     * Insertion synchrone d'une row IN_PROGRESS au demarrage du workflow .
     * Best-effort : si la DB est down , on log warn mais on laisse passer
     * pour ne pas tuer un import qui pourrait survivre a une panne DB
     * transitoire ( au pire on rebascule en mode "ecriture finale only" ) .
     *
     * <p>Apres cet appel , toute terminaison ( COMPLETED / FAILED / CANCELLED )
     * passera par {@link #logAsync} qui UPDATE la row existante via UPSERT .
     */
    public void recordStart(WorkflowLogEntry start) {
        if (start == null) {
            return;
        }
        // Bounded retries with backoff to survive transient DB hiccups
        // ( connection pool drain , DB restart in flight ) . Without this
        // safety net , a workflow that starts during a brief DB outage
        // would never appear in workflow_log -> invisible in History
        // and Integrity views forever .
        long[] backoffMs = { 200L, 500L, 1_000L };
        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= backoffMs.length; attempt++) {
            try {
                boolean inserted = repository.recordStart(start);
                if (!inserted) {
                    log.debug("recordStart : row deja presente pour {} ( retry ? )", start.correlationId());
                }
                if (attempt > 0) {
                    log.info("recordStart : entry persistee apres {} retries pour {}",
                            attempt, start.correlationId());
                }
                return;
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("recordStart attempt {}/{} failed for {} : {}",
                        attempt + 1, backoffMs.length + 1, start.correlationId(), e.getMessage());
            }
            if (attempt < backoffMs.length) {
                try {
                    Thread.sleep(backoffMs[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.warn("recordStart : all retries failed for {} : {} ; workflow continues in degraded mode "
                        + "( the IN_PROGRESS row is missing , the {@code WorkflowZombieSweeper} cannot detect the workflow if it dies ; "
                        + "the row will be inserted at terminal time via recordEnd if the DB recovers )",
                start.correlationId(), lastError == null ? "unknown" : lastError.getMessage());
    }

    /**
     * Persist a terminal workflow event ( COMPLETED / FAILED / CANCELLED )
     * synchronously , with bounded retries before falling back to the
     * async queue . Contrasts with {@link #logAsync} which fire-and-forget
     * via the queue ; the async path drops entries on flush failure
     * ( DB temporarily down ) , which is unacceptable for terminal events :
     * losing a FAILED row leaves the workflow stuck IN_PROGRESS forever
     * ( until ZombieSweeper kicks in 5 min later ) and , worse , out of the
     * History / Integrity views .
     *
     * <p>Retry policy : 3 attempts with exponential backoff ( 0.5s , 1s ,
     * 2s ) . Total worst-case latency ~ 3.5s before fallback to async
     * queue . Acceptable for a workflow that just terminated ; admins
     * see the row immediately if DB is healthy , see it via the async
     * queue otherwise ( eventually consistent ) .
     *
     * <p>The fallback to async queue is a last resort : if the queue is
     * full ( DB durably down + many workflows finishing ) , the entry is
     * dropped . The {@code WorkflowZombieSweeper} catches it later via
     * the IN_PROGRESS row left by {@link #recordStart} .
     *
     * @return {@code true} if persisted synchronously , {@code false} if
     *         the entry was queued for async retry ( or dropped if queue
     *         full )
     */
    public boolean recordEnd(WorkflowLogEntry entry) {
        if (entry == null) {
            return false;
        }
        long[] backoffMs = { 500L, 1_000L, 2_000L };
        for (int attempt = 0; attempt < backoffMs.length; attempt++) {
            try {
                int inserted = repository.insertBatch(java.util.List.of(entry));
                if (inserted > 0) {
                    if (attempt > 0) {
                        log.info("recordEnd : entry persistee apres {} retries pour {}",
                                attempt, entry.correlationId());
                    }
                    return true;
                }
                // P0-3 cancel-divergence observability : inserted == 0 signifie
                // que le SQL record_workflow a filtre la row ( WHERE status =
                // 'IN_PROGRESS' ) - typiquement parce que le watchdog ou un
                // cancel utilisateur a deja marque la row terminale . Avant
                // P0-1+P0-2 ce silent no-op masquait la divergence ; on log
                // ERROR pour rendre la trace visible en cas de regression
                // d'invariant ( un workflow Phase 2 ne devrait JAMAIS voir
                // sa row marquee terminale par un tiers grace au heartbeat
                // P0-1 + FOR UPDATE P0-2 ; si ca arrive , c'est un signal
                // de regression a investiguer ) .
                log.error("recordEnd : SQL record_workflow returned 0 rows for {} "
                                + "( row probably already terminal - watchdog race or cancel ) "
                                + "status={} fatalError={} - investigate if invariant regressed",
                        entry.correlationId(), entry.status(), entry.fatalError());
                return false;
            } catch (RuntimeException ex) {
                log.warn("recordEnd attempt {}/{} failed for {} : {}",
                        attempt + 1, backoffMs.length, entry.correlationId(), ex.getMessage());
            }
            try {
                Thread.sleep(backoffMs[attempt]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Tous les retries ont echoue : fallback queue async . Le
        // {@code WorkflowZombieSweeper} sera notre filet final si la
        // queue droppe aussi ( DB durablement down ) .
        log.warn("recordEnd : all sync retries failed for {} ; fallback async queue",
                entry.correlationId());
        logAsync(entry);
        return false;
    }

    /**
     * Empile une entry pour insertion async. Ne bloque jamais le thread
     * appelant : si la queue est pleine , l'entry est droppee et un
     * warning est logue.
     *
     * <p>Pour les events terminaux ( COMPLETED / FAILED / CANCELLED ) ,
     * preferer {@link #recordEnd} qui retry synchrone avant fallback
     * async . Reserve {@code logAsync} aux events non-critiques .
     */
    public void logAsync(WorkflowLogEntry entry) {
        if (entry == null) {
            return;
        }
        if (!queue.offer(entry)) {
            log.warn("Queue de WorkflowLogWriter pleine ( {} elements ) , entry droppee : {} {}",
                    queue.size(), entry.workflowType(), entry.correlationId());
        }
    }

    private void runLoop() {
        List<WorkflowLogEntry> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                WorkflowLogEntry head = queue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                if (head != null) {
                    batch.add(head);
                    queue.drainTo(batch, batchSize - batch.size());
                }
                if (!batch.isEmpty()) {
                    flush(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erreur inattendue dans le writer loop , on continue", e);
            }
        }
        // Drain final au shutdown
        if (!queue.isEmpty()) {
            List<WorkflowLogEntry> remaining = new ArrayList<>(queue.size());
            queue.drainTo(remaining);
            flush(remaining);
        }
    }

    private void flush(List<WorkflowLogEntry> batch) {
        try {
            int inserted = repository.insertBatch(batch);
            if (log.isDebugEnabled()) {
                log.debug("Flush WorkflowLogWriter : {}/{} entries inserees", inserted, batch.size());
            }
        } catch (Exception e) {
            log.error("Echec flush WorkflowLogWriter de {} entries , elles sont perdues", batch.size(), e);
            // Volontaire : on ne re-queue pas , ca risquerait de former un
            // boucle infinie si la DB est durablement en erreur.
        }
    }

    @PreDestroy
    void stop() {
        this.running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("WorkflowLogWriter ne s'est pas arrete en 10s , {} entries potentiellement perdues",
                            queue.size());
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        log.info("WorkflowLogWriter arrete");
    }
}
