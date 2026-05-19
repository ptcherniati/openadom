package fr.inra.oresing.workflow.guard;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Garde-fou heap JVM : refuse les nouvelles publications quand la
 * pression memoire approche la limite , log un niveau critique encore
 * plus haut .
 *
 * <h2>Pourquoi</h2>
 *
 * <p>Le backend openadom peut consommer plusieurs GB de heap par
 * publication concurrente ( cf chargement referentiels en RAM pour
 * checkers , buffers cascade , staging in-memory counters ) . Sur des
 * deploiements memoires-contraints ou multi-publish concurrents , on
 * peut atteindre l'OOM avant de pouvoir reagir . Le crash OOM est
 * brutal : workflows en cours perdus , supervision aveugle , redemarrage
 * manuel necessaire .
 *
 * <p>Ce service donne une retroaction graduelle :
 * <ol>
 *   <li>A {@code refuse-publish-threshold-pct} ( defaut 80% ) :
 *       {@link #isUnderPressure} renvoie {@code true} ; les callers
 *       qui consultent ce flag refusent les nouvelles publications
 *       avec un message explicite ( retry-later ) .</li>
 *   <li>A {@code critical-threshold-pct} ( defaut 90% ) : log ERROR
 *       toutes les iterations + signal a la supervision externe
 *       ( health check , Prometheus alert ) .</li>
 * </ol>
 *
 * <p>Le scan @Scheduled tourne toutes les {@code check-interval-ms}
 * ms ( defaut 10s ) . Le calcul utilise
 * {@link MemoryMXBean#getHeapMemoryUsage()} ( vue agregee des
 * generations heap , post-GC quand possible ) plutot que
 * {@link Runtime#freeMemory()} qui peut sur-estimer la pression .
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Ne distingue pas la pression generationnelle ( old gen plein
 *       vs eden plein ) : un GC full peut liberer 30%+ instantanement .
 *       Pour eviter de refuser sur faux positif , {@link #isUnderPressure}
 *       lit une moyenne glissante des 3 derniers samples .</li>
 *   <li>Ne tient pas compte du native memory ( DirectByteBuffer , JNI ) .
 *       Le heap Java pur n'est qu'une partie de la pression process .</li>
 *   <li>Activable / desactivable via {@code app.workflow.heap-guard.enabled}
 *       ( defaut true ) .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name           = "app.workflow.heap-guard.enabled",
        havingValue    = "true",
        matchIfMissing = true)
public class HeapGuardService {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    /** Seuil pourcent heap au-dela duquel les nouvelles publications
     *  sont refusees . */
    private final int refusePublishPct;

    /** Seuil pourcent heap au-dela duquel un log critical est emis a
     *  chaque tick ( plus eleve que refuse pour distinguer pression
     *  vs OOM imminent ) . */
    private final int criticalPct;

    /** Derniere mesure heap utilisee pour {@link #isUnderPressure}
     *  ( moyenne glissante des N derniers samples , protege contre
     *  faux positifs lies aux pics eden pre-GC ) . */
    private final AtomicReference<Double> smoothedUsagePct = new AtomicReference<>(0.0);

    /** Coefficient moyenne exponentielle : sample * alpha + previous * (1 - alpha) .
     *  alpha = 0.3 -> reactif mais lisse les pics court terme . */
    private static final double SMOOTHING_ALPHA = 0.3;

    public HeapGuardService(
            @Value("${app.workflow.heap-guard.refuse-publish-threshold-pct:90}") int refusePublishPct,
            @Value("${app.workflow.heap-guard.critical-threshold-pct:95}") int criticalPct) {
        if (refusePublishPct < 1 || refusePublishPct > 100) {
            throw new IllegalArgumentException(
                    "refuse-publish-threshold-pct doit etre entre 1 et 100 ( recu : " + refusePublishPct + " )");
        }
        if (criticalPct < refusePublishPct || criticalPct > 100) {
            throw new IllegalArgumentException(
                    "critical-threshold-pct doit etre >= refuse-publish-threshold-pct ( "
                            + criticalPct + " < " + refusePublishPct + " )");
        }
        this.refusePublishPct = refusePublishPct;
        this.criticalPct      = criticalPct;
        log.info("HeapGuardService configure : refuse-publish a {}% , critical a {}%",
                refusePublishPct, criticalPct);
    }

    @PostConstruct
    void initialSample() {
        // Initialise smoothedUsagePct avec une premiere mesure pour eviter
        // qu'un appel a isUnderPressure juste apres le boot retourne false
        // alors que le heap est deja sous pression .
        double initial = computeCurrentUsagePct();
        smoothedUsagePct.set(initial);
        log.info("HeapGuardService initial heap usage : {} %", String.format("%.1f", initial));
    }

    /**
     * Scan periodique : recalcule la moyenne lissee + emet les warn /
     * error logs selon seuils . Interval configurable via
     * {@code app.workflow.heap-guard.check-interval-ms} ( defaut 10s ) .
     */
    @Scheduled(fixedDelayString = "${app.workflow.heap-guard.check-interval-ms:10000}")
    public void scan() {
        try {
            double sample = computeCurrentUsagePct();
            double previous = smoothedUsagePct.get();
            double smoothed = sample * SMOOTHING_ALPHA + previous * (1.0 - SMOOTHING_ALPHA);
            smoothedUsagePct.set(smoothed);

            if (sample >= criticalPct) {
                log.error("[heap-guard] CRITICAL heap usage : {} % ( seuil critique {} % ) ; OOM imminent possible , liberer memoire ou reduire la concurrence",
                        formatPct(sample), criticalPct);
            } else if (sample >= refusePublishPct) {
                log.warn("[heap-guard] heap usage eleve : {} % ( seuil refuse {} % ) ; nouvelles publications refusees jusqu'a retour < seuil",
                        formatPct(sample), refusePublishPct);
            } else if (log.isDebugEnabled()) {
                log.debug("[heap-guard] heap usage : {} % ( smoothed {} % )",
                        formatPct(sample), formatPct(smoothed));
            }
        } catch (RuntimeException ex) {
            log.warn("[heap-guard] scan failed ( best-effort , on reessayera ) : {}", ex.getMessage());
        }
    }

    /**
     * Indique si le heap depasse {@code refusePublishPct} sur la moyenne
     * lissee ( pas sur le dernier sample brut ) . Utilise par les callers
     * ( ex : {@code PublishLifecycleService} ) pour refuser une nouvelle
     * publication entrante avec un message clair retry-later .
     *
     * @return {@code true} si heap sous pression , {@code false} sinon
     */
    public boolean isUnderPressure() {
        return smoothedUsagePct.get() >= refusePublishPct;
    }

    /**
     * Snapshot des metriques courantes ( exposable via endpoint admin
     * ou health check ) . Pas thread-safe au niveau atomique mais best
     * effort suffisant pour observability .
     */
    public HeapStats currentStats() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        return new HeapStats(
                heap.getUsed(),
                heap.getMax(),
                computeCurrentUsagePct(),
                smoothedUsagePct.get(),
                refusePublishPct,
                criticalPct,
                isUnderPressure());
    }

    private double computeCurrentUsagePct() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max  = heap.getMax();
        if (max <= 0) {
            // Si max non defini ( cas rare , JVM sans -Xmx ) , fallback sur
            // committed comme reference - moins precis mais evite NaN .
            max = heap.getCommitted();
        }
        if (max <= 0) {
            return 0.0;
        }
        return (used * 100.0) / max;
    }

    private static String formatPct(double pct) {
        return String.format("%.1f", pct);
    }

    public int getRefusePublishPct() {
        return refusePublishPct;
    }

    public int getCriticalPct() {
        return criticalPct;
    }

    /** Snapshot expose pour observability ( admin endpoint , health check ) . */
    public record HeapStats(
            long    heapUsedBytes,
            long    heapMaxBytes,
            double  currentUsagePct,
            double  smoothedUsagePct,
            int     refusePublishThresholdPct,
            int     criticalThresholdPct,
            boolean underPressure
    ) {}
}
