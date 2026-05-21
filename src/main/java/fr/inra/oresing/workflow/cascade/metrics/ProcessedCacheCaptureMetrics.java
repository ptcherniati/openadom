package fr.inra.oresing.workflow.cascade.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metriques Prometheus pour la capture du Large Object {@code binaryfile.processed_data}
 * ( workflow PUBLISH + BUILD_CACHE admin ) .
 *
 * <h2>Pourquoi cette metric</h2>
 *
 * <p>Le bug critique "Fuite_de_transactions_apres_import" ( 2026-05-20 )
 * a montre qu'un deadlock COPY / LargeObject sur la meme connexion JDBC
 * pouvait laisser une transaction ouverte plusieurs heures , epingler
 * le {@code xmin horizon} et bloquer l'autovacuum . Le fix
 * ( BinaryFileRepository : split 2-phases + 2 connexions ) est en place
 * depuis le commit f078111b ; cette metric permet de detecter en prod
 * toute regression future ( capture qui prend &gt; 10 min , erreurs
 * recurrentes ) avant qu'elle ne degrade la base .
 *
 * <h2>Metriques exposees</h2>
 *
 * <ul>
 *   <li>{@code oa_processed_cache_capture_duration_seconds} : timer
 *       histogramme , label {@code outcome} parmi
 *       {@code success | failed | skipped_disabled | skipped_up_to_date} .
 *       Permet alertes Grafana du type "p95 &gt; 10 min" .</li>
 *   <li>{@code oa_processed_cache_capture_bytes} : distribution des
 *       tailles de cache produites ( informationnel , aide a
 *       dimensionner le stockage Large Object ) . Enregistre uniquement
 *       sur outcome=success .</li>
 * </ul>
 *
 * <p>Prefix {@code oa_processed_cache_*} coherent avec
 * {@link OpenadomCacheMetrics} ( {@code oa_cache_*} ) et
 * {@link OpenadomMetrics} ( {@code oa_import_*} , {@code oa_extraction_*} ) .
 *
 * @author R.YAHIAOUI
 * @since 2026-05-21
 */
@Slf4j
@Component
public class ProcessedCacheCaptureMetrics {

    private static final String METRIC_DURATION = "oa_processed_cache_capture_duration_seconds";
    private static final String METRIC_BYTES = "oa_processed_cache_capture_bytes";
    private static final String TAG_OUTCOME = "outcome";

    /**
     * Outcomes possibles de la capture . Enum interne pour eviter les
     * magic strings dispersees dans les call-sites .
     */
    public enum Outcome {
        SUCCESS("success"),
        FAILED("failed"),
        SKIPPED_DISABLED("skipped_disabled"),
        SKIPPED_UP_TO_DATE("skipped_up_to_date");

        private final String label;
        Outcome(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final MeterRegistry registry;
    private final DistributionSummary bytesSummary;

    public ProcessedCacheCaptureMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.bytesSummary = DistributionSummary.builder(METRIC_BYTES)
                .description("Size of the captured processed_data Large Object ( bytes ) per successful capture")
                .baseUnit("bytes")
                .register(registry);
        log.info("ProcessedCacheCaptureMetrics ready : {} ( histogram timer ) + {} ( bytes distribution )",
                METRIC_DURATION, METRIC_BYTES);
    }

    /**
     * Demarre un sample de duree . Le caller doit appeler
     * {@link #stop(Timer.Sample, Outcome)} dans son {@code finally} pour
     * garantir l'enregistrement meme en cas d'exception .
     */
    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    /**
     * Arrete le sample et enregistre la duree sur le timer
     * {@code oa_processed_cache_capture_duration_seconds} avec le label
     * {@code outcome} approprie .
     */
    public void stop(Timer.Sample sample, Outcome outcome) {
        if (sample == null || outcome == null) return;
        sample.stop(Timer.builder(METRIC_DURATION)
                .description("Duration of the processed_data capture flow ( PUBLISH post-DONE + BUILD_CACHE admin )")
                .tag(TAG_OUTCOME, outcome.label())
                .publishPercentileHistogram()
                .register(registry));
    }

    /**
     * Enregistre la taille du cache produit ( bytes ecrits dans le
     * Large Object ) . A appeler uniquement sur succes ; les outcomes
     * skip / failed n'ont pas de payload mesurable .
     */
    public void recordBytes(long bytes) {
        if (bytes <= 0) return;
        bytesSummary.record(bytes);
    }

    /**
     * Helper pour mesurer une operation a la fois ( pattern try-finally
     * encapsule ) . Utilisable quand le call-site veut juste
     * {@code metrics.measure(Outcome.SUCCESS , () -> operation())} sans
     * gerer manuellement le Timer.Sample .
     *
     * <p>Reserve aux operations rapides ; pour les flows complexes a
     * plusieurs points d'echec , preferer
     * {@link #startSample()} + {@link #stop(Timer.Sample, Outcome)} .
     */
    public long measure(Outcome outcome, java.util.function.LongSupplier op) {
        Timer.Sample sample = startSample();
        try {
            return op.getAsLong();
        } finally {
            stop(sample, outcome);
        }
    }

    /** Utilitaire teste : convertir duree nano en secondes pour la log . */
    static double nanosToSeconds(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos) / 1_000.0;
    }
}
