package fr.inra.oresing.workflow.cascade.history;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evenement de log d'un workflow finalise ( import ou extraction ).
 *
 * <p>Record immutable construit au moment de la finalisation du workflow
 * puis transmis au {@link WorkflowLogWriter} pour insertion async dans
 * {@code oa_audit.workflow_log}.
 *
 * <p>Phase 2 observabilite (issue #62).
 *
 * @param correlationId    identifiant unique du workflow
 * @param workflowType     IMPORT , EXTRACT_ZIP , EXTRACT_CSV , etc.
 * @param userId           identifiant utilisateur ( UUID )
 * @param userLogin        login utilisateur ( denormalise , survit a la suppression du user )
 * @param applicationName  nom de l'application concernee
 * @param dataType         type de donnee ou reference
 * @param resourceName     nom du fichier ou ressource
 * @param startTime        demarrage
 * @param endTime          finalisation
 * @param duration         duree totale
 * @param status           IN_PROGRESS / COMPLETED / FAILED / CANCELLED / RATE_LIMITED
 * @param recordsProcessed lignes traitees avec succes ( imports uniquement )
 * @param recordsFailed    lignes en erreur ( imports uniquement )
 * @param chunksProcessed  chunks cascade achevs ( imports uniquement )
 * @param bytesTotal       volume total ( bytes entrants pour import , sortants pour extract )
 * @param errors           liste des messages d'erreur non fatals
 * @param fatalError       exception fatale ( message + stack )
 */
public record WorkflowLogEntry(
        UUID          correlationId,
        String        workflowType,
        UUID          userId,
        String        userLogin,
        String        applicationName,
        String        dataType,
        String        resourceName,
        Instant       startTime,
        Instant       endTime,
        Duration      duration,
        String        status,
        long          recordsProcessed,
        long          recordsFailed,
        int           chunksProcessed,
        long          bytesTotal,
        List<String>  errors,
        String        fatalError,
        /** Champ extensible JSONB persiste tel quel ; contient
         *  parallelisme + strategy + JVM stats pour aider l'admin a
         *  trouver la config optimale en post-mortem . */
        Map<String, Object> metadata,
        /**
         * Stage cascade ou la failure a ete attribuee ( cascade 2.2.0
         * {@code WorkflowResult.failedStage} ) : SOURCE | TRANSFORM |
         * COLLECTOR | SINK | TEARDOWN | UNKNOWN . NULL pour status
         * non FAILED .
         */
        String        failedStage,
        /**
         * Compteur authoritatif COUNT(*) FROM &lt;app&gt;.referencevalue WHERE
         * binaryfile = ? capture au markCompleted ( post-afterCommit Phase B ) .
         * NULL pour les statuses non terminaux ou si le COUNT a echoue
         * post-commit ( DB transitoirement down ) . Source de verite pour
         * IntegrityService et DashboardService.finalizeProgress qui evite
         * les COUNT repetes au poll . Cf AUDIT 06-05-26 #1 .
         */
        Long          finalCount) {

    /** Compat constructor : entries sans metadata , failedStage , finalCount . */
    public WorkflowLogEntry(
            UUID correlationId, String workflowType, UUID userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startTime, Instant endTime, Duration duration, String status,
            long recordsProcessed, long recordsFailed, int chunksProcessed,
            long bytesTotal, List<String> errors, String fatalError) {
        this(correlationId, workflowType, userId, userLogin,
             applicationName, dataType, resourceName,
             startTime, endTime, duration, status,
             recordsProcessed, recordsFailed, chunksProcessed,
             bytesTotal, errors, fatalError, null, null, null);
    }

    /** Compat constructor : entries sans failedStage ni finalCount . */
    public WorkflowLogEntry(
            UUID correlationId, String workflowType, UUID userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startTime, Instant endTime, Duration duration, String status,
            long recordsProcessed, long recordsFailed, int chunksProcessed,
            long bytesTotal, List<String> errors, String fatalError,
            Map<String, Object> metadata) {
        this(correlationId, workflowType, userId, userLogin,
             applicationName, dataType, resourceName,
             startTime, endTime, duration, status,
             recordsProcessed, recordsFailed, chunksProcessed,
             bytesTotal, errors, fatalError, metadata, null, null);
    }

    /** Compat constructor : entries sans finalCount . */
    public WorkflowLogEntry(
            UUID correlationId, String workflowType, UUID userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startTime, Instant endTime, Duration duration, String status,
            long recordsProcessed, long recordsFailed, int chunksProcessed,
            long bytesTotal, List<String> errors, String fatalError,
            Map<String, Object> metadata, String failedStage) {
        this(correlationId, workflowType, userId, userLogin,
             applicationName, dataType, resourceName,
             startTime, endTime, duration, status,
             recordsProcessed, recordsFailed, chunksProcessed,
             bytesTotal, errors, fatalError, metadata, failedStage, null);
    }

    public static final String TYPE_IMPORT                    = "IMPORT";
    public static final String TYPE_EXTRACT_ZIP               = "EXTRACT_ZIP";
    public static final String TYPE_EXTRACT_CSV               = "EXTRACT_CSV";
    public static final String TYPE_EXTRACT_ADDITIONAL_FILES  = "EXTRACT_ADDITIONAL_FILES";
    public static final String TYPE_EXTRACT_CHARTE            = "EXTRACT_CHARTE";

    public static final String STATUS_IN_PROGRESS   = "IN_PROGRESS";
    public static final String STATUS_COMPLETED     = "COMPLETED";
    public static final String STATUS_FAILED        = "FAILED";
    public static final String STATUS_CANCELLED     = "CANCELLED";
    public static final String STATUS_RATE_LIMITED  = "RATE_LIMITED";

    // Phases in-progress publiees dans WorkflowActiveRegistry pour oa-live .
    // Ne sont pas persistees dans oa_audit.workflow_log ( qui ne contient
    // que IN_PROGRESS au demarrage puis les etats terminaux ci-dessus ;
    // les phases intra-execution restent en memoire ) .
    public static final String STATUS_UPLOADING     = "UPLOADING";
    public static final String STATUS_CHUNKING      = "CHUNKING";
    public static final String STATUS_PROCESSING    = "PROCESSING";
    public static final String STATUS_LOADING_DB    = "LOADING_DB";

    /**
     * Construit l'entry minimale persistee au demarrage du workflow par
     * {@link WorkflowLogWriter#recordStart} . Les compteurs / errors /
     * fatalError sont nuls a ce stade ; ils seront remplis par l'entry
     * terminale via UPSERT .
     */
    public static WorkflowLogEntry startMarker(
            UUID correlationId, String workflowType, UUID userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startTime, long bytesTotal) {
        return new WorkflowLogEntry(
                correlationId, workflowType, userId, userLogin,
                applicationName, dataType, resourceName,
                startTime, null, null, STATUS_IN_PROGRESS,
                0L, 0L, 0, bytesTotal, List.of(), null, null, null, null);
    }
}
