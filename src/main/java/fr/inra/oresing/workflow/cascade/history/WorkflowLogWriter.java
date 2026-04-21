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
 * Writer asynchrone pour {@code oa_metrics.workflow_log}.
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
     * Empile une entry pour insertion async. Ne bloque jamais le thread
     * appelant : si la queue est pleine , l'entry est droppee et un
     * warning est logue.
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
