package fr.inra.oresing.workflow.cascade.history;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Detecte les workflows zombies dans {@code oa_audit.workflow_log} et
 * les passe a {@code CANCELLED} avec {@code fatal_error = 'presumed dead'} .
 *
 * <p>Un zombie = row IN_PROGRESS dont {@code start_time} est anterieur a
 * {@code app.workflow.zombie-threshold-minutes} ( defaut 30 min ) . Cause
 * la plus frequente : SIGKILL ( docker kill , OOM JVM , panne machine )
 * entre le {@link WorkflowLogWriter#recordStart} synchrone et l'event
 * terminal async ( fenetre dans laquelle aucun signal de terminaison n'a
 * pu atteindre la base ) .
 *
 * <p>Sans ce sweeper , la row IN_PROGRESS resterait coincee indefiniment
 * et polluerait le dashboard ( workflow apparemment toujours en cours
 * mais en realite mort depuis longtemps ) .
 *
 * <p>Pilote par :
 * <ul>
 *   <li>{@code app.workflow.zombie-threshold-minutes} : seuil ( min )
 *       sur {@code COALESCE ( last_heartbeat_at , start_time )} . Defaut
 *       10 min depuis V5 ( heartbeat emit toutes les 30 sec par
 *       {@link HeartbeatService} pendant les phases longues ; marge x20
 *       qui tolere GC pauses + DB hiccups ) . Pour un deploy sans
 *       heartbeat ( pre-V5 , phase courte ) , relever a 30+ pour
 *       eviter les faux positifs .</li>
 *   <li>{@code app.workflow.zombie-sweep-cron} : frequence de scan
 *       ( cron Spring ) . Defaut : toutes les 5 minutes .</li>
 * </ul>
 *
 * <p>Requiert {@code @EnableScheduling} active au niveau application
 * ( cf OreSiNg ) , au meme titre que les autres sweepers .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name           = "app.workflow.zombie-enabled",
        havingValue    = "true",
        matchIfMissing = true)
public class WorkflowZombieSweeper {

    private final WorkflowLogRepository repository;
    /**
     * Volatile pour autoriser la mutation à chaud via {@code ConfigEditPanel}
     * admin ( cf {@code ConfigFieldRegistry.workflow.zombieThresholdMinutes} ) .
     * La valeur est lue à chaque tick {@link #sweepZombies} : pas de cache ,
     * pas de redémarrage requis pour appliquer un changement .
     */
    private volatile int                thresholdMinutes;

    /**
     * Active le cleanup zombie au boot ( {@link #cleanupOrphansOnBoot} ) . Si
     * {@code true} ( defaut ) , toute row {@code IN_PROGRESS} existante au
     * demarrage JVM est passee a {@code CANCELLED} immediatement , sans
     * attendre le seuil normal . Hypothese : aucune workflow ne peut avoir
     * survecu a un redemarrage de cette instance ; les rows orphelines
     * proviennent forcement de la JVM precedente .
     *
     * <p>En deploiement multi-instances , desactiver via
     * {@code app.workflow.zombie-cleanup-on-boot=false} pour eviter de
     * tuer les workflows legitimes des autres instances .
     */
    private final boolean cleanupOnBoot;

    public WorkflowZombieSweeper(
            WorkflowLogRepository repository,
            @Value("${app.workflow.zombie-threshold-minutes:10}") int thresholdMinutes,
            @Value("${app.workflow.zombie-cleanup-on-boot:true}") boolean cleanupOnBoot) {
        this.repository       = repository;
        this.thresholdMinutes = thresholdMinutes;
        this.cleanupOnBoot    = cleanupOnBoot;
        log.info("WorkflowZombieSweeper configure : seuil={} min , cleanupOnBoot={} ( IN_PROGRESS plus vieux que ca = presumes morts )",
                thresholdMinutes, cleanupOnBoot);
    }

    public int getThresholdMinutes() {
        return thresholdMinutes;
    }

    public void setThresholdMinutes(int v) {
        if (v < 1) {
            throw new IllegalArgumentException("zombieThresholdMinutes must be >= 1 ( got " + v + " )");
        }
        int old = this.thresholdMinutes;
        this.thresholdMinutes = v;
        log.info("WorkflowZombieSweeper threshold change : {} min -> {} min",
                old, v);
    }

    /**
     * Au boot Spring , passe immediatement tous les workflows {@code IN_PROGRESS}
     * a {@code CANCELLED} avec {@code fatal_error = 'presumed dead at boot'} .
     * Ces rows ne peuvent etre que des orphelins d'une JVM precedente
     * ( restart , rebuild , crash ) - cette instance vient de demarrer , aucun
     * workflow ne peut etre vivant sous sa supervision .
     *
     * <p>Sans ce cleanup , le {@code Reject 409} ( UNIQUE partial index sur
     * workflow_log per fileId ) refuserait toute nouvelle PUBLISH / UNPUBLISH
     * sur les fichiers concernes jusqu'a ce que le sweeper periodique trigger
     * apres le seuil ( defaut 10 min ) , causant une fenetre 10 min de
     * blocage user post-redeploy ( bug observe en dev ) .
     *
     * <p>En multi-instances ce comportement est inadequat ( les autres
     * instances peuvent avoir des workflows legitimes ) : desactiver via
     * {@code app.workflow.zombie-cleanup-on-boot=false} .
     */
    @PostConstruct
    void cleanupOrphansOnBoot() {
        if (!cleanupOnBoot) {
            log.info("Zombie cleanup on boot : skipped ( app.workflow.zombie-cleanup-on-boot=false )");
            return;
        }
        try {
            int n = repository.markAllInProgressAsOrphans();
            if (n > 0) {
                log.warn("Zombie cleanup on boot : {} workflow(s) IN_PROGRESS orphan(s) de la JVM precedente passes a CANCELLED",
                        n);
            } else {
                log.info("Zombie cleanup on boot : aucune row orpheline detectee");
            }
        } catch (RuntimeException ex) {
            log.warn("Zombie cleanup on boot failed : {} ( le sweeper periodique prendra le relais )",
                    ex.getMessage());
        }
    }

    /**
     * Scan + flag des zombies . Defaut : toutes les 5 minutes . Le sweep
     * est leger ( UPDATE indexed sur status='IN_PROGRESS' ) , donc une
     * frequence elevee n'est pas couteuse .
     */
    @Scheduled(cron = "${app.workflow.zombie-sweep-cron:0 */5 * * * *}")
    public void sweepZombies() {
        try {
            long start = System.currentTimeMillis();
            int n = repository.markZombies(thresholdMinutes);
            long durationMs = System.currentTimeMillis() - start;
            if (n > 0) {
                log.warn("Zombie sweeper : {} workflow(s) IN_PROGRESS plus vieux que {} min passes a CANCELLED en {} ms",
                        n, thresholdMinutes, durationMs);
            } else {
                log.debug("Zombie sweeper : aucun zombie detecte ( seuil {} min )", thresholdMinutes);
            }
        } catch (Exception e) {
            log.error("Echec sweep zombies , on retentera au prochain tick", e);
        }
    }
}
