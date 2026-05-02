package fr.inra.oresing.workflow.cascade.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tache periodique qui purge les entries anciennes de
 * {@code oa_audit.workflow_log} pour respecter la retention configuree.
 *
 * <p>Fenetre de retention pilotee par {@code app.metrics.retention-days}
 * ( defaut 30 , remplace par la variable d'environnement
 * {@code APP_METRICS_RETENTION_DAYS} en docker-compose ).
 *
 * <p>Execution par defaut : tous les jours a 3h du matin ( cron
 * {@code 0 0 3 * * *} ). Pilotable par {@code app.metrics.retention-cron}.
 *
 * <p>Requiert {@code @EnableScheduling} active au niveau application
 * ( cf OreSiNg ).
 *
 * <p>Phase 2 observabilite (issue #62).
 */
@Slf4j
@Component
public class WorkflowLogRetentionTask {

    private final WorkflowLogRepository repository;
    private final int                   retentionDays;

    public WorkflowLogRetentionTask(
            WorkflowLogRepository repository,
            @Value("${app.metrics.retention-days:30}") int retentionDays) {
        this.repository    = repository;
        this.retentionDays = retentionDays;
        log.info("WorkflowLogRetentionTask configure : retention de {} jours", retentionDays);
    }

    /**
     * Execute la purge une fois par jour a 3h du matin. Le cron est
     * surcharg-able via {@code app.metrics.retention-cron}.
     */
    @Scheduled(cron = "${app.metrics.retention-cron:0 0 3 * * *}")
    public void purgeOldEntries() {
        try {
            long start = System.currentTimeMillis();
            int deleted = repository.deleteOlderThan(retentionDays);
            long durationMs = System.currentTimeMillis() - start;
            if (deleted > 0) {
                log.info("Retention workflow_log : {} entries supprimees en {} ms ( > {} jours )",
                        deleted, durationMs, retentionDays);
            } else {
                log.debug("Retention workflow_log : aucune entry a supprimer");
            }
        } catch (Exception e) {
            log.error("Echec de la purge workflow_log , on retentera au prochain tick", e);
        }
    }
}
