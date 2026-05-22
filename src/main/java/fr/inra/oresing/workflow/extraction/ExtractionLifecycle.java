package fr.inra.oresing.workflow.extraction;

import fr.inra.oresing.workflow.cascade.history.*;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cycle de vie unifié pour les flows d'extraction ( ZIP / CSV / charte /
 * additional files / futurs ) . Centralise tout le boilerplate qui était
 * auparavant dupliqué dans chaque endpoint :
 *
 * <ul>
 *   <li>{@link WorkflowLogWriter#recordStart} : pré-persistance row
 *       IN_PROGRESS dans {@code oa_audit.workflow_log} ( permet à
 *       l'historique de voir le workflow dès le début , même si le client
 *       déconnecte avant la fin ) .</li>
 *   <li>{@link WorkflowActiveRegistry#start} : publication du snapshot
 *       in-memory ( permet à la vue temps-réel oa-live de voir
 *       l'extraction en cours ) .</li>
 *   <li>{@link HeartbeatService#start} : heartbeat lifecycle pipeline
 *       ( évite que le sweeper marque le workflow "presumed dead" pendant
 *       les longs streamings ) .</li>
 *   <li>{@link OpenadomMetrics#markExtractionStart} / {@code markExtractionEnd} :
 *       métriques Prometheus .</li>
 * </ul>
 *
 * <p><b>Usage</b> ( pattern try-with-resources ) :
 *
 * <pre>{@code
 * try (ExtractionLifecycle.Handle h = extractionLifecycle.start(
 *         WorkflowLogEntry.TYPE_EXTRACT_ZIP, userId, userLogin,
 *         applicationName, dataType, resourceName, fileSizeBytes)) {
 *     // ... business logic streaming ...
 *     h.complete(bytesStreamed);
 * } catch (Exception e) {
 *     // h.close() s'occupe de fail() automatiquement si complete() pas appelé
 *     throw e;
 * }
 * }</pre>
 *
 * <p>Le {@link Handle} est {@link AutoCloseable} : si le caller oublie
 * {@code complete()} ou {@code fail()} , {@code close()} appelle
 * automatiquement {@code fail()} avec un message générique , garantissant
 * qu'aucun workflow ne reste bloqué en IN_PROGRESS .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class ExtractionLifecycle {

    private final WorkflowLogWriter        logWriter;
    private final WorkflowActiveRegistry   activeRegistry;
    private final HeartbeatService         heartbeatService;
    private final OpenadomMetrics          metrics;

    public ExtractionLifecycle(
            WorkflowLogWriter logWriter,
            WorkflowActiveRegistry activeRegistry,
            HeartbeatService heartbeatService,
            OpenadomMetrics metrics) {
        this.logWriter        = logWriter;
        this.activeRegistry   = activeRegistry;
        this.heartbeatService = heartbeatService;
        this.metrics          = metrics;
    }

    /**
     * Démarre le lifecycle d'une extraction et retourne le handle à
     * utiliser dans un try-with-resources . Le handle expose
     * {@link Handle#complete} ou {@link Handle#fail} pour finaliser
     * proprement ; le {@link Handle#close} ( appelé par le try-with )
     * sert de safety net si aucun des deux n'a été invoqué .
     *
     * @param workflowType   un des TYPE_EXTRACT_* de {@link WorkflowLogEntry}
     * @param userId         id utilisateur ( UUID )
     * @param userLogin      login pour denormalisation ( peut être null )
     * @param applicationName  nom de l'application ( peut être null )
     * @param dataType       type de donnée ou ressource ( peut être null )
     * @param resourceName   nom de la ressource demandée ( fichier , etc. )
     * @param bytesTotal     volume estimé en entrée ( 0 si inconnu )
     */
    public Handle start(String workflowType, UUID userId, String userLogin,
                        String applicationName, String dataType, String resourceName,
                        long bytesTotal) {
        UUID corrId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        // 1. Persiste IN_PROGRESS dans workflow_log : visible immédiatement
        //    dans l'historique , même si le client déconnecte avant la fin .
        try {
            logWriter.recordStart(WorkflowLogEntry.startMarker(
                    corrId, workflowType, userId, userLogin,
                    applicationName, dataType, resourceName,
                    startedAt, bytesTotal));
        } catch (RuntimeException e) {
            // Best-effort : un échec de persistance ne doit pas casser
            // l'extraction . Le finalize fera fallback sur l'INSERT direct .
            log.warn("ExtractionLifecycle : recordStart failed for {} ( degraded mode ) : {}",
                    corrId, e.getMessage());
        }

        // 2. Publie le snapshot in-memory : visible dans la vue temps-réel
        //    oa-live ( /api/dashboard/workflows/in-progress ) .
        try {
            activeRegistry.start(new WorkflowSnapshot(
                    corrId, workflowType, userId, userLogin,
                    applicationName, dataType, resourceName,
                    startedAt, WorkflowLogEntry.STATUS_IN_PROGRESS,
                    0L, 0L, 0,
                    null,                 // progressPercentage : inconnue pour streaming
                    bytesTotal,
                    0L,                   // recordsTotal : inconnu pour extraction
                    List.of(),            // errors
                    List.of(),            // chunks ( extraction non chunkée )
                    List.of(),            // workers ( extraction non parallélisée )
                    null,                 // parallelism
                    null,                 // strategy
                    List.of(),            // sinkChunks
                    null,                 // importConfig
                    null));               // lastHeartbeatAt ( renseigné par HeartbeatService )
        } catch (RuntimeException e) {
            log.warn("ExtractionLifecycle : registry.start failed for {} ( degraded mode ) : {}",
                    corrId, e.getMessage());
        }

        // 3. Heartbeat lifecycle pipeline-wide : couvre tout le streaming
        //    pour éviter que le sweeper marque "presumed dead" .
        HeartbeatService.Heartbeat hb = heartbeatService.start(corrId);

        // 4. Métriques Prometheus
        String metricsType = workflowTypeToMetricsKey(workflowType);
        metrics.markExtractionStart(metricsType);

        return new Handle(corrId, workflowType, metricsType,
                userId, userLogin, applicationName, dataType, resourceName,
                startedAt, hb);
    }

    /**
     * Mappe les constantes WorkflowLogEntry.TYPE_EXTRACT_* vers la clé
     * courte utilisée par les métriques Prometheus ( "zip" , "csv" , etc. ) .
     */
    private static String workflowTypeToMetricsKey(String workflowType) {
        return switch (workflowType) {
            case WorkflowLogEntry.TYPE_EXTRACT_ZIP              -> "zip";
            case WorkflowLogEntry.TYPE_EXTRACT_CSV              -> "csv";
            case WorkflowLogEntry.TYPE_EXTRACT_CHARTE           -> "charte";
            case WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES -> "additional";
            default                                              -> workflowType.toLowerCase();
        };
    }

    /**
     * Handle de cycle de vie d'une extraction . À fermer impérativement
     * via {@link #complete} ou {@link #fail} ; le {@link #close} ( appelé
     * par try-with-resources ) est un safety net qui appelle
     * {@code fail("not finalized")} si rien n'a été appelé .
     */
    public final class Handle implements AutoCloseable {

        private final UUID    correlationId;
        private final String  workflowType;
        private final String  metricsType;
        private final UUID    userId;
        private final String  userLogin;
        private final String  applicationName;
        private final String  dataType;
        private final String  resourceName;
        private final Instant startedAt;
        private final HeartbeatService.Heartbeat heartbeat;

        private boolean finalized = false;

        Handle(UUID correlationId, String workflowType, String metricsType,
               UUID userId, String userLogin,
               String applicationName, String dataType, String resourceName,
               Instant startedAt,
               HeartbeatService.Heartbeat heartbeat) {
            this.correlationId   = correlationId;
            this.workflowType    = workflowType;
            this.metricsType     = metricsType;
            this.userId          = userId;
            this.userLogin       = userLogin;
            this.applicationName = applicationName;
            this.dataType        = dataType;
            this.resourceName    = resourceName;
            this.startedAt       = startedAt;
            this.heartbeat       = heartbeat;
        }

        public UUID correlationId() { return correlationId; }

        /**
         * Finalise le workflow en succès avec le rowcount streamé .
         * Idempotent : un 2ème appel ( ou close() après ) est no-op .
         */
        public void complete(long bytesStreamed) {
            finalize0(WorkflowLogEntry.STATUS_COMPLETED, bytesStreamed, null);
        }

        /**
         * Finalise le workflow en échec . Idempotent .
         */
        public void fail(Throwable error) {
            String msg = error == null ? "unknown error"
                    : error.getClass().getSimpleName() + ": " + error.getMessage();
            finalize0(WorkflowLogEntry.STATUS_FAILED, 0L, msg);
        }

        /**
         * Safety net : si ni complete() ni fail() n'ont été appelés ,
         * marque FAILED avec un message générique . Garantit qu'aucun
         * workflow ne reste bloqué en IN_PROGRESS .
         */
        @Override
        public void close() {
            if (!finalized) {
                finalize0(WorkflowLogEntry.STATUS_FAILED, 0L,
                        "Extraction not finalized ( neither complete() nor fail() called )");
            }
        }

        private void finalize0(String status, long bytesStreamed, String fatalError) {
            if (finalized) return;
            finalized = true;

            // Stop heartbeat AVANT l UPDATE final ( idempotent côté
            // HeartbeatService ) . Évite qu un beat tardif ( race
            // condition entre cancel et tick ) ne réouvre la row en
            // IN_PROGRESS après notre flip .
            try { heartbeat.close(); } catch (RuntimeException ignore) { /* best-effort */ }

            Duration duration = Duration.between(startedAt, Instant.now());

            // Métriques
            try {
                metrics.recordExtractionCompleted(metricsType, applicationName, dataType,
                        status, duration, bytesStreamed);
            } catch (RuntimeException e) {
                log.warn("ExtractionLifecycle.finalize : metrics failed : {}", e.getMessage());
            }
            try {
                metrics.markExtractionEnd(metricsType);
            } catch (RuntimeException e) {
                log.warn("ExtractionLifecycle.finalize : markExtractionEnd failed : {}", e.getMessage());
            }

            // Persiste l'entry terminale ( synchrone + retry via recordEnd )
            try {
                logWriter.recordEnd(new WorkflowLogEntry(
                        correlationId, workflowType, userId, userLogin,
                        applicationName, dataType, resourceName,
                        startedAt, startedAt.plus(duration), duration,
                        status,
                        0L, 0L, 0,            // records ( extraction n'a pas de notion de records )
                        bytesStreamed,
                        List.of(), fatalError));
            } catch (RuntimeException e) {
                log.warn("ExtractionLifecycle.finalize : recordEnd failed for {} ( sweeper rattrapera ) : {}",
                        correlationId, e.getMessage());
            }

            // Retire du registry live
            try {
                activeRegistry.finish(correlationId);
            } catch (RuntimeException ignore) { /* best-effort */ }
        }
    }
}