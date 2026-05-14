package fr.inra.oresing.monitoring.session;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Writer asynchrone pour {@code oa_audit.user_session_log} . Pattern
 * identique a
 * {@link fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter} :
 * queue bornee + thread daemon batch INSERT . L'observabilite ne doit
 * jamais ralentir la production .
 *
 * <p>Active par defaut ; flag {@code app.session.persist-enabled=false}
 * desactive l'insertion DB ( la queue est court-circuitee tres tot pour
 * ne pas allouer ) .
 */
@Slf4j
@Service
public class UserSessionLogWriter {

    private final UserSessionLogRepository repository;
    private final boolean persistEnabled;
    private final int     batchSize;
    private final long    flushIntervalMs;

    private BlockingQueue<UserSessionLogEntry> queue;
    private ExecutorService                    executor;
    private volatile boolean                   running;

    public UserSessionLogWriter(
            UserSessionLogRepository repository,
            @Value("${app.session.persist-enabled:true}") boolean persistEnabled,
            @Value("${app.session.writer.queue-capacity:5000}") int queueCapacity,
            @Value("${app.session.writer.batch-size:50}") int batchSize,
            @Value("${app.session.writer.flush-interval-ms:2000}") long flushIntervalMs) {
        this.repository      = repository;
        this.persistEnabled  = persistEnabled;
        this.queue           = new LinkedBlockingQueue<>(queueCapacity);
        this.batchSize       = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        if (persistEnabled) {
            log.info("UserSessionLogWriter config : queueCapacity={} , batchSize={} , flushIntervalMs={} ms",
                    queueCapacity, batchSize, flushIntervalMs);
        } else {
            log.info("UserSessionLogWriter persist-enabled=false : sessions ne seront pas archivees en DB");
        }
    }

    @PostConstruct
    void start() {
        if (!persistEnabled) return;
        this.running = true;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "user-session-log-writer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::runLoop);
        log.info("UserSessionLogWriter demarre");
    }

    @PreDestroy
    void stop() {
        this.running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    /**
     * Empile une entry pour insertion async . Ne bloque jamais : queue
     * pleine = entry droppee + warning ( pareil que WorkflowLogWriter ) .
     */
    public void logAsync(UserSessionLogEntry entry) {
        if (!persistEnabled || entry == null) return;
        if (!queue.offer(entry)) {
            log.warn("Queue UserSessionLogWriter pleine ( {} entries ) , entry droppee : {}",
                    queue.size(), entry.sessionId());
        }
    }

    private void runLoop() {
        List<UserSessionLogEntry> batch = new ArrayList<>(batchSize);
        long lastFlush = System.currentTimeMillis();
        while (running || !queue.isEmpty()) {
            try {
                UserSessionLogEntry head = queue.poll(200, TimeUnit.MILLISECONDS);
                if (head != null) {
                    batch.add(head);
                    queue.drainTo(batch, batchSize - batch.size());
                }
                long now = System.currentTimeMillis();
                boolean shouldFlush = !batch.isEmpty()
                        && (batch.size() >= batchSize || now - lastFlush >= flushIntervalMs);
                if (shouldFlush) {
                    flush(batch);
                    batch.clear();
                    lastFlush = now;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                log.warn("UserSessionLogWriter loop : exception non-fatale : {}", ex.getMessage(), ex);
            }
        }
        if (!batch.isEmpty()) flush(batch);                                                  // best-effort drain
        log.info("UserSessionLogWriter arrete");
    }

    private void flush(List<UserSessionLogEntry> batch) {
        try {
            int inserted = repository.insertBatch(batch);
            log.debug("UserSessionLogWriter : {} sessions persistees ( batch size {} )", inserted, batch.size());
        } catch (RuntimeException ex) {
            // Aucune relance auto : l'observabilite est best-effort par design .
            log.warn("UserSessionLogWriter : echec INSERT batch ( {} entries ) : {}",
                    batch.size(), ex.getMessage(), ex);
        }
    }
}