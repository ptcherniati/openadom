package fr.inra.oresing.monitoring.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Filet de securite : tourne a 3h du matin par defaut , trouve les rows
 * compensation_log restees PENDING au-dela du TTL ( signe que le finally
 * du caller n'a pas pu s'executer , ex. JVM kill , crash , DB hiccup ) ,
 * et invoque le handler associe pour cleanup .
 *
 * <p>Activable / desactivable via {@code app.compensation.sweep.enabled} .
 * Cron configurable via {@code app.compensation.sweep.cron} .
 *
 * <p>Le sweeper N'INTERVIENT PAS sur les rows en cours d'op normale :
 * il attend que le TTL ( default 4h ) soit depasse avant de toucher
 * une row . Donc 0 risque de race avec les imports en cours .
 *
 * @author R.YAHIAOUI
 */
@Component
@ConditionalOnProperty(name = "app.compensation.sweep.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CompensationSweeper {

    private final CompensationLogService service;

    @Value("${app.compensation.sweep.batch-size:100}")
    private int batchSize;

    @Value("${app.compensation.failed-retention-days:7}")
    private int failedRetentionDays;

    /**
     * Cron par defaut : 3h du matin tous les jours .
     * Override via {@code app.compensation.sweep.cron} ou env var
     * {@code APP_COMPENSATION_SWEEP_CRON} .
     */
    @Scheduled(cron = "${app.compensation.sweep.cron:0 0 3 * * *}")
    public void sweep() {
        long start = System.currentTimeMillis();
        int compensated = 0, failed = 0;

        List<CompensationLogEntry> stale = service.findStalePendingForSweep(batchSize);
        log.info("CompensationSweeper : {} stale PENDING rows found ( ttl exceeded )", stale.size());

        for (CompensationLogEntry entry : stale) {
            boolean ok = service.runHandlerAndDelete(entry);
            if (ok) compensated++; else failed++;
        }

        // Rotation FAILED rows older than retention
        int purged = service.deleteFailedOlderThan(failedRetentionDays);
        if (purged > 0) {
            log.info("CompensationSweeper : purged {} FAILED rows older than {} days",
                    purged, failedRetentionDays);
        }

        long durationMs = System.currentTimeMillis() - start;
        log.info("CompensationSweeper done : compensated={} , failed={} , purged={} , duration={}ms",
                compensated, failed, purged, durationMs);
    }

    /** Permet de declencher manuellement le sweeper depuis l'endpoint admin . */
    public SweepResult sweepNow() {
        long start = System.currentTimeMillis();
        int compensated = 0, failed = 0;
        List<CompensationLogEntry> stale = service.findStalePendingForSweep(batchSize);
        for (CompensationLogEntry entry : stale) {
            if (service.runHandlerAndDelete(entry)) compensated++;
            else failed++;
        }
        return new SweepResult(stale.size(), compensated, failed,
                System.currentTimeMillis() - start);
    }

    public record SweepResult(int totalProcessed, int compensated, int failed, long durationMs) { }
}
