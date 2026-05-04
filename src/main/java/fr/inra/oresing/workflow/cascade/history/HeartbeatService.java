package fr.inra.oresing.workflow.cascade.history;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emet des heartbeats periodiques sur {@code oa_audit.workflow_log} pour
 * les workflows en phase longue ( finalize hook ) . Ferme le trou des
 * faux positifs zombie sur les workflows lents legitimes ( gros UPSERT ,
 * DB lente , reseau slow ) .
 *
 * <h2>Strategy : thread dedie ( "B" )</h2>
 *
 * <p>Plutot que d'inserer manuellement des appels {@code repository.beat}
 * dans la loop UPSERT ( strategy A ) , on lance un {@link ScheduledExecutorService}
 * qui beat toutes les {@code intervalSeconds} secondes en arriere-plan .
 *
 * <p>Avantages :
 * <ul>
 *   <li>Heartbeat garanti meme si une operation SQL bloque longtemps
 *       ( un seul gros batch UPSERT de 5 min ne casse pas la liveness ) .</li>
 *   <li>Code metier ( finalize ) reste simple : pas de polluants
 *       {@code if (i % N == 0)} dans la loop .</li>
 *   <li>Nettoyage clean via try-with-resources : le {@link Heartbeat}
 *       implements {@link AutoCloseable} -&gt; le scheduler stop a la
 *       sortie du bloc , garanti meme en cas d'exception .</li>
 * </ul>
 *
 * <p>Cout : 1 thread daemon par finalize en cours . Le thread fait 1
 * UPDATE indexed toutes les 30 sec -&gt; cost CPU / DB negligeable .
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * try (Heartbeat hb = heartbeatService.start(corrId)) {
 *     finalizeHook.runUpsert();
 * }
 * // hb.close() arrete le scheduler , garanti meme en cas d'exception .
 * }</pre>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Service
public class HeartbeatService {

    private final WorkflowLogRepository  repository;
    private final WorkflowActiveRegistry registry;
    private final long                   intervalSeconds;
    private ScheduledExecutorService     scheduler;

    public HeartbeatService(
            WorkflowLogRepository repository,
            WorkflowActiveRegistry registry,
            @Value("${app.workflow.heartbeat-interval-seconds:30}") long intervalSeconds) {
        this.repository      = repository;
        this.registry        = registry;
        this.intervalSeconds = intervalSeconds;
    }

    @PostConstruct
    void init() {
        // Pool partage pour tous les heartbeats actifs . Single-threaded
        // suffit ( 1 UPDATE indexed par 30 sec * N workflows ; meme avec
        // 100 workflows simultanes le thread n'est jamais sature ) .
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workflow-heartbeat");
            t.setDaemon(true);
            return t;
        });
        log.info("HeartbeatService configure : interval={} sec", intervalSeconds);
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
    }

    /**
     * Demarre l'emission de heartbeats periodiques pour le workflow
     * {@code correlationId} . Le caller doit imperativement
     * {@link Heartbeat#close()} ( de preference via try-with-resources )
     * pour stopper le scheduler . Sinon le heartbeat continuera jusqu'a
     * l'arret de la JVM ( pas de fuite memoire mais pollution log ) .
     *
     * <p>Le 1er beat est emit immediatement ( delay=0 ) pour marquer
     * le debut de la phase ; les suivants toutes les
     * {@code intervalSeconds} secondes .
     */
    public Heartbeat start(UUID correlationId) {
        if (correlationId == null) {
            return Heartbeat.NOOP;
        }
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    java.time.Instant now = java.time.Instant.now();
                    repository.beat(correlationId);
                    registry.setLastHeartbeat(correlationId, now);
                },
                0L,
                intervalSeconds,
                TimeUnit.SECONDS);
        log.debug("Heartbeat started for {} ( interval={} sec )", correlationId, intervalSeconds);
        return new Heartbeat(future, correlationId);
    }

    /**
     * Handle returned by {@link #start} . Implements {@link AutoCloseable}
     * pour usage try-with-resources . Idempotent : double-close OK .
     */
    public static final class Heartbeat implements AutoCloseable {

        static final Heartbeat NOOP = new Heartbeat(null, null);

        private final ScheduledFuture<?> future;
        private final UUID               correlationId;

        Heartbeat(ScheduledFuture<?> future, UUID correlationId) {
            this.future        = future;
            this.correlationId = correlationId;
        }

        @Override
        public void close() {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
                log.debug("Heartbeat stopped for {}", correlationId);
            }
        }
    }
}
