package fr.inra.oresing.workflow.cascade.metrics;

import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facade unique centralisant toutes les metrics Prometheus custom
 * d'OpenADOM ( imports et extractions ).
 *
 * <p>Principes :
 * <ul>
 *   <li><b>Non-intrusive</b> : chaque site d'appel metier n'ajoute
 *       qu'une seule ligne, sans logique metric embedded.</li>
 *   <li><b>Fail-safe</b> : toutes les methodes publiques wrappees en
 *       try/catch. Un bug dans le code metric ne peut jamais casser
 *       un import ou une extraction.</li>
 *   <li><b>Utilisation idiomatique Micrometer</b> : appels directs aux
 *       APIs Counter / Timer / Gauge, pas d'abstraction custom.</li>
 *   <li><b>Labels bas-cardinalite uniquement</b> : application,
 *       data_type, status, type. Jamais user_id ni correlation_id
 *       ( ceux-ci iront dans la table DB en phase 2 ).</li>
 * </ul>
 *
 * <p>Les Gauges "in progress" sont enregistres au demarrage et
 * Micrometer les poll automatiquement :
 * <ul>
 *   <li>Import : valeur lue depuis {@link WorkflowEventBus}
 *       cascade ( zero code metier additionnel ).</li>
 *   <li>Extraction : valeur lue depuis des compteurs
 *       {@link AtomicInteger} locaux, alimentes via
 *       {@link #markExtractionStart(String)} et
 *       {@link #markExtractionEnd(String)} depuis les controllers.</li>
 * </ul>
 *
 * <p>Phase 1 observabilite (issue #62).
 */
@Slf4j
@Component
public class OpenadomMetrics {

    // Constantes tags
    public static final String TAG_APPLICATION = "application";
    public static final String TAG_DATA_TYPE   = "data_type";
    public static final String TAG_STATUS      = "status";
    public static final String TAG_TYPE        = "type";
    public static final String TAG_STAGE       = "stage";
    public static final String VALUE_UNKNOWN   = "unknown";

    // Prefixes metrics
    private static final String IMPORT_PREFIX     = "oa_import";
    private static final String EXTRACTION_PREFIX = "oa_extraction";

    // Compteurs internes pour les gauges extraction ( une entree par type )
    private final Map<String, AtomicInteger> extractionInProgress = new ConcurrentHashMap<>();

    private final MeterRegistry registry;

    public OpenadomMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Enregistrement des gauges au demarrage. Les gauges sont des
     * fonctions que Micrometer invoque periodiquement pour scraper
     * leur valeur courante.
     */
    @PostConstruct
    void registerGauges() {
        try {
            // Import : nombre de workflows cascade actifs
            registry.gauge(IMPORT_PREFIX + "_in_progress", Tags.empty(),
                    WorkflowEventBus.getInstance(),
                    service -> service.getAllActiveWorkflows().size());

            // Extraction : un gauge par type, alimente via AtomicInteger
            for (String type : new String[] { "zip", "csv", "additional_files" }) {
                AtomicInteger counter = extractionInProgress
                        .computeIfAbsent(type, k -> new AtomicInteger());
                registry.gauge(EXTRACTION_PREFIX + "_in_progress",
                        Tags.of(TAG_TYPE, type),
                        counter,
                        AtomicInteger::get);
            }
            log.info("OpenadomMetrics ready : gauges import + extraction enregistres");
        } catch (Exception e) {
            log.warn("Echec enregistrement des gauges OpenadomMetrics", e);
        }
    }

    // ========================================================================
    //  API IMPORTS
    // ========================================================================

    /**
     * Enregistre la fin d'un import ( succes ou echec ).
     *
     * @param application     nom de l'application ( null -> "unknown" )
     * @param dataType        type de reference/data ( null -> "unknown" )
     * @param status          COMPLETED / FAILED / CANCELLED
     * @param duration        duree totale de l'import
     * @param recordsProcessed lignes traitees avec succes
     * @param recordsFailed   lignes en erreur
     * @param chunksProcessed chunks cascade achevs
     * @param fileSizeBytes   taille du fichier d'entree en octets
     */
    public void recordImportCompleted(
            String application, String dataType, String status,
            Duration duration, long recordsProcessed, long recordsFailed,
            int chunksProcessed, long fileSizeBytes) {
        try {
            Tags coreTags = Tags.of(
                    TAG_APPLICATION, safe(application),
                    TAG_DATA_TYPE,   safe(dataType));
            Tags withStatus = coreTags.and(TAG_STATUS, safe(status));

            Counter.builder(IMPORT_PREFIX + "_total")
                    .description("Nombre d'imports finalises, toutes issues confondues")
                    .tags(withStatus)
                    .register(registry)
                    .increment();

            Timer.builder(IMPORT_PREFIX + "_duration_seconds")
                    .description("Duree des imports")
                    .publishPercentileHistogram()
                    .tags(coreTags)
                    .register(registry)
                    .record(duration);

            if (recordsProcessed > 0) {
                Counter.builder(IMPORT_PREFIX + "_records_processed_total")
                        .description("Lignes CSV traitees avec succes")
                        .tags(coreTags)
                        .register(registry)
                        .increment(recordsProcessed);
            }
            if (recordsFailed > 0) {
                Counter.builder(IMPORT_PREFIX + "_records_failed_total")
                        .description("Lignes CSV en erreur")
                        .tags(coreTags)
                        .register(registry)
                        .increment(recordsFailed);
            }
            if (chunksProcessed > 0) {
                Counter.builder(IMPORT_PREFIX + "_chunks_processed_total")
                        .description("Chunks cascade traites")
                        .tags(coreTags)
                        .register(registry)
                        .increment(chunksProcessed);
            }
            if (fileSizeBytes > 0) {
                Counter.builder(IMPORT_PREFIX + "_bytes_total")
                        .description("Volume cumule de fichiers importes")
                        .baseUnit("bytes")
                        .tags(coreTags)
                        .register(registry)
                        .increment(fileSizeBytes);
            }
        } catch (Exception e) {
            log.warn("Echec enregistrement metrics import {}/{}", application, dataType, e);
        }
    }

    /**
     * Enregistre un import en echec , taggue avec le {@code stage} cascade
     * ( {@code SOURCE} / {@code TRANSFORM} / {@code COLLECTOR} /
     * {@code SINK} / {@code TEARDOWN} / {@code UNKNOWN} ) recupere depuis
     * {@code WorkflowResult.failedStage()} ( cascade 2.2.0 ) .
     *
     * <p>Ecrit deux series de metriques :
     * <ul>
     *   <li>{@code oa_import_total{status=FAILED,application,data_type}} :
     *       compteur des imports echoues ( meme dimension que les succes ,
     *       pour le ratio sur le dashboard "Failure rate" ) .</li>
     *   <li>{@code oa_import_failed_total{stage,application,data_type}} :
     *       compteur dedie au stage attribue par cascade , utilise par
     *       Grafana pour le panel "Failures by stage" et l'alerting
     *       cible sur les phases critiques ( ex TEARDOWN -&gt; finalize
     *       hook FK violation ) .</li>
     * </ul>
     *
     * @param application      nom de l'application ( null -&gt; "unknown" )
     * @param dataType         type de reference / data ( null -&gt; "unknown" )
     * @param failedStage      stage cascade ( {@code WorkflowStage.name()} ) ,
     *                         null sera mappe sur {@code "unknown"}
     * @param duration         duree totale de l'import
     * @param recordsProcessed lignes traitees avec succes avant l'echec
     * @param recordsFailed    lignes en erreur
     * @param chunksProcessed  chunks cascade achevs avant l'echec
     * @param fileSizeBytes    taille du fichier d'entree en octets
     * @since cascade 2.2.0 + workflow_log.failed_stage column
     */
    public void recordImportFailed(
            String application, String dataType, String failedStage,
            Duration duration, long recordsProcessed, long recordsFailed,
            int chunksProcessed, long fileSizeBytes) {
        try {
            // Garde-fou : reuse la meme combinaison de tags que recordImportCompleted
            // pour que le ratio "FAILED / total" soit calculable cote Grafana .
            recordImportCompleted(application, dataType, "FAILED",
                    duration, recordsProcessed, recordsFailed, chunksProcessed, fileSizeBytes);

            Tags failedTags = Tags.of(
                    TAG_APPLICATION, safe(application),
                    TAG_DATA_TYPE,   safe(dataType),
                    TAG_STAGE,       safe(failedStage));

            Counter.builder(IMPORT_PREFIX + "_failed_total")
                    .description("Nombre d'imports en echec , dimension par stage cascade ( SOURCE / "
                            + "TRANSFORM / COLLECTOR / SINK / TEARDOWN / UNKNOWN )")
                    .tags(failedTags)
                    .register(registry)
                    .increment();
        } catch (Exception e) {
            log.warn("Echec enregistrement metrics recordImportFailed {}/{}/{}",
                    application, dataType, failedStage, e);
        }
    }

    /**
     * Enregistre un rejet par le rate-limiter d'imports.
     */
    public void recordImportRateLimited() {
        try {
            Counter.builder(IMPORT_PREFIX + "_rate_limited_total")
                    .description("Rejets 429 Too Many Requests sur les imports")
                    .register(registry)
                    .increment();
        } catch (Exception e) {
            log.warn("Echec enregistrement metric import rate-limited", e);
        }
    }

    // ========================================================================
    //  API EXTRACTIONS
    // ========================================================================

    /**
     * Enregistre la fin d'une extraction ( succes ou echec ).
     *
     * @param type         zip / csv / additional_files
     * @param application  nom de l'application ( null -> "unknown" )
     * @param dataType     type de donnees ( null -> "unknown" )
     * @param status       COMPLETED / FAILED / CANCELLED
     * @param duration     duree totale de l'extraction
     * @param bytesStreamed volume cumule ecrit en sortie
     */
    public void recordExtractionCompleted(
            String type, String application, String dataType, String status,
            Duration duration, long bytesStreamed) {
        try {
            Tags coreTags = Tags.of(
                    TAG_TYPE,        safe(type),
                    TAG_APPLICATION, safe(application),
                    TAG_DATA_TYPE,   safe(dataType));
            Tags withStatus = coreTags.and(TAG_STATUS, safe(status));

            Counter.builder(EXTRACTION_PREFIX + "_total")
                    .description("Nombre d'extractions finalisees, toutes issues confondues")
                    .tags(withStatus)
                    .register(registry)
                    .increment();

            Timer.builder(EXTRACTION_PREFIX + "_duration_seconds")
                    .description("Duree des extractions")
                    .publishPercentileHistogram()
                    .tags(coreTags)
                    .register(registry)
                    .record(duration);

            if (bytesStreamed > 0) {
                Counter.builder(EXTRACTION_PREFIX + "_bytes_total")
                        .description("Volume cumule streame vers les clients")
                        .baseUnit("bytes")
                        .tags(coreTags)
                        .register(registry)
                        .increment(bytesStreamed);
            }
        } catch (Exception e) {
            log.warn("Echec enregistrement metrics extraction {}/{}/{}", type, application, dataType, e);
        }
    }

    /**
     * Enregistre un rejet par le rate-limiter d'extractions.
     *
     * @param type zip / csv / additional_files
     */
    public void recordExtractionRateLimited(String type) {
        try {
            Counter.builder(EXTRACTION_PREFIX + "_rate_limited_total")
                    .description("Rejets 429 Too Many Requests sur les extractions")
                    .tags(TAG_TYPE, safe(type))
                    .register(registry)
                    .increment();
        } catch (Exception e) {
            log.warn("Echec enregistrement metric extraction rate-limited {}", type, e);
        }
    }

    /**
     * Incremente le compteur in-progress pour le type d'extraction
     * donne. A appeler au DEBUT d'une extraction , avant le
     * streaming.
     */
    public void markExtractionStart(String type) {
        try {
            extractionInProgress
                    .computeIfAbsent(safe(type), k -> new AtomicInteger())
                    .incrementAndGet();
        } catch (Exception e) {
            log.warn("Echec markExtractionStart pour type {}", type, e);
        }
    }

    /**
     * Decremente le compteur in-progress pour le type d'extraction
     * donne. A appeler a la FIN du streaming , dans un finally.
     */
    public void markExtractionEnd(String type) {
        try {
            AtomicInteger counter = extractionInProgress.get(safe(type));
            if (counter != null) {
                counter.decrementAndGet();
            }
        } catch (Exception e) {
            log.warn("Echec markExtractionEnd pour type {}", type, e);
        }
    }

    // ========================================================================

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? VALUE_UNKNOWN : value;
    }
}
