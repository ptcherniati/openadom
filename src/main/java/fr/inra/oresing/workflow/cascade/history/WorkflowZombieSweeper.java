package fr.inra.oresing.workflow.cascade.history;

import fr.inra.oresing.mail.Email;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

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

    /**
     * Email service via ObjectProvider ( lazy resolution ) pour notifier
     * l'admin lors de la detection de zombies UNPUBLISH / DELETE_FILE
     * marques FAILED par migration V12 .
     *
     * <p>Pourquoi ObjectProvider et pas {@code @Autowired Email} : un
     * autowire direct sur {@link Email} declenche
     * {@code MailSenderAutoConfiguration} de Spring Boot . Sur les
     * profils de tests sans mail config ( ex : testmail in-memory ) ,
     * cette autoconfig echoue et fait cascader la failure du contexte
     * Spring entier ( MigrateService bean non-cree -> tous les tests
     * d'integration cassent ) . ObjectProvider est une dependance
     * differee : Spring ne touche pas a l'autoconfig mail tant que
     * {@code getIfAvailable()} n'est pas appele a runtime .
     */
    @Autowired
    private ObjectProvider<Email> emailProvider;

    /** Adresse email destinataire des alertes zombies UNPUBLISH / DELETE_FILE .
     *  Vide ( defaut ) -> notifications desactivees . Configurer via
     *  {@code app.workflow.zombie-notify-email=admin@example.com} . */
    @Value("${app.workflow.zombie-notify-email:}")
    private String notifyEmail;

    /** Base URL frontend pour generer le lien "Reprendre" dans l'email .
     *  Defaut sur {@code openadom.front.base-url} ( cf Phase 1 #487 ) . */
    @Value("${openadom.front.base-url:}")
    private String frontBaseUrl;

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
                notifyFailedUnpublishZombiesBestEffort();
            } else {
                log.debug("Zombie sweeper : aucun zombie detecte ( seuil {} min )", thresholdMinutes);
            }
        } catch (Exception e) {
            log.error("Echec sweep zombies , on retentera au prochain tick", e);
        }
    }

    /**
     * Notifie l'admin par email des zombies UNPUBLISH / DELETE_FILE
     * fraichement marques FAILED ( cf migration V12 : ces types sont
     * marques FAILED au lieu de CANCELLED pour signaler que l'utilisateur
     * doit relancer l'operation pour terminer le nettoyage des donnees ) .
     *
     * <p>Best-effort : aucune exception ne remonte ; un echec d'envoi
     * est logge en warn et le sweeper continue ( prochain tick reessayera
     * sur les zombies suivants ) .
     *
     * <p>No-op si :
     * <ul>
     *   <li>{@code app.workflow.zombie-notify-email} est vide ;</li>
     *   <li>{@code emailService} bean non-disponible ( profil test ) ;</li>
     *   <li>aucun zombie FAILED dans la fenetre des 90 secondes
     *       precedentes ( marge sur le cron 5 min ) .</li>
     * </ul>
     */
    private void notifyFailedUnpublishZombiesBestEffort() {
        if (notifyEmail == null || notifyEmail.isBlank()) {
            return;
        }
        Email emailService = emailProvider != null ? emailProvider.getIfAvailable() : null;
        if (emailService == null) {
            log.debug("Notification zombie skip : Email bean non-disponible");
            return;
        }
        try {
            List<WorkflowLogRepository.FailedZombieRow> failed =
                    repository.findRecentlyFailedZombies(90);
            for (WorkflowLogRepository.FailedZombieRow row : failed) {
                try {
                    emailService.sendEmail(
                            "openadom",
                            notifyEmail,
                            buildEmailSubject(row),
                            buildEmailBody(row));
                    log.info("Notification zombie envoyee a {} pour workflow {} ( type={} , fichier={} )",
                            notifyEmail, row.correlationId(), row.workflowType(), row.resourceName());
                } catch (RuntimeException ex) {
                    log.warn("Echec envoi notification zombie pour workflow {} : {}",
                            row.correlationId(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Echec query failed zombies pour notification : {}", ex.getMessage());
        }
    }

    private static String buildEmailSubject(WorkflowLogRepository.FailedZombieRow row) {
        String action = "UNPUBLISH".equals(row.workflowType())
                ? "Depublication" : "Suppression";
        return "[OpenADOM] " + action + " interrompue : " + row.resourceName();
    }

    private String buildEmailBody(WorkflowLogRepository.FailedZombieRow row) {
        String action = "UNPUBLISH".equals(row.workflowType())
                ? "depublication" : "suppression";
        StringBuilder b = new StringBuilder(512);
        b.append("Une operation de ").append(action).append(" a ete interrompue ")
                .append("et marquee FAILED par le sweeper zombie .\n\n");
        b.append("Workflow id  : ").append(row.correlationId()).append('\n');
        b.append("Type         : ").append(row.workflowType()).append('\n');
        b.append("Utilisateur  : ").append(row.userLogin()).append('\n');
        b.append("Application  : ").append(row.applicationName()).append('\n');
        b.append("Datatype     : ").append(row.dataType()).append('\n');
        b.append("Fichier      : ").append(row.resourceName()).append('\n');
        if (row.startTime() != null) {
            b.append("Demarre le   : ")
                    .append(DateTimeFormatter.ISO_INSTANT.format(row.startTime())).append('\n');
        }
        if (row.endTime() != null) {
            b.append("Termine le   : ")
                    .append(DateTimeFormatter.ISO_INSTANT.format(row.endTime())).append('\n');
        }
        b.append("\nMotif :\n").append(row.fatalError()).append("\n\n");
        b.append("Les donnees peuvent etre partiellement supprimees . ")
                .append("Relancez la ").append(action).append(" depuis l'interface ")
                .append("pour terminer le nettoyage ( operation idempotente ) .\n");
        if (frontBaseUrl != null && !frontBaseUrl.isBlank()) {
            b.append("\nLien historique : ")
                    .append(frontBaseUrl).append("/oa-live/#/history?cid=")
                    .append(row.correlationId()).append('\n');
        }
        return b.toString();
    }
}
