package fr.inra.oresing.workflow.cascade.history;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evenement de log d'un workflow finalise ( import ou extraction ).
 *
 * <p>Record immutable construit au moment de la finalisation du workflow
 * puis transmis au {@link WorkflowLogWriter} pour insertion async dans
 * {@code oa_metrics.workflow_log}.
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
        String        fatalError) {

    public static final String TYPE_IMPORT                    = "IMPORT";
    public static final String TYPE_EXTRACT_ZIP               = "EXTRACT_ZIP";
    public static final String TYPE_EXTRACT_CSV               = "EXTRACT_CSV";
    public static final String TYPE_EXTRACT_ADDITIONAL_FILES  = "EXTRACT_ADDITIONAL_FILES";
    public static final String TYPE_EXTRACT_CHARTE            = "EXTRACT_CHARTE";

    public static final String STATUS_COMPLETED     = "COMPLETED";
    public static final String STATUS_FAILED        = "FAILED";
    public static final String STATUS_CANCELLED     = "CANCELLED";
    public static final String STATUS_RATE_LIMITED  = "RATE_LIMITED";
}
