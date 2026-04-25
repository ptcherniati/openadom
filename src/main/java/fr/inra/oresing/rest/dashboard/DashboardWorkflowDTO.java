package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Summary DTO returned by /api/dashboard/workflows/in-progress and
 * /api/dashboard/workflows/history. Mirrors {@link fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot}
 * and the columns of oa_metrics.workflow_log.
 *
 * <p>Fields that are only meaningful while a workflow is still running
 * ( progressPercentage , chunksProcessed ) or only after it ends
 * ( endTime , durationMs , fatalError ) can be null.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(
        name = "DashboardWorkflow",
        description = "Workflow ( import or extraction ) summary consumed by the oa-live dashboard")
public record DashboardWorkflowDTO(

        @Schema(description = "Correlation id - stable identifier across logs / metrics / DB row")
        UUID correlationId,

        @Schema(description = "Workflow type",
                allowableValues = {"IMPORT", "EXTRACT_ZIP", "EXTRACT_CSV",
                        "EXTRACT_ADDITIONAL_FILES", "EXTRACT_CHARTE"})
        String workflowType,

        @Schema(description = "Owner of the workflow")
        UUID userId,

        @Schema(description = "Login of the user ( nullable if user was deleted )")
        String userLogin,

        @Schema(description = "Application the workflow runs against ( nullable for global extractions )")
        String applicationName,

        @Schema(description = "Data type inside the application ( CSV reference type )")
        String dataType,

        @Schema(description = "File / resource name ( uploaded file or requested resource )")
        String resourceName,

        @Schema(description = "Workflow start time ( ISO-8601 )")
        Instant startTime,

        @Schema(description = "End time - null if still running")
        Instant endTime,

        @Schema(description = "Duration in milliseconds - null if still running")
        Long durationMs,

        @Schema(description = "Current status",
                allowableValues = {"IN_PROGRESS", "COMPLETED", "FAILED", "CANCELLED", "RATE_LIMITED"})
        String status,

        @Schema(description = "Records / rows processed so far")
        long recordsProcessed,

        @Schema(description = "Records / rows that failed validation or insertion")
        long recordsFailed,

        @Schema(description = "Chunks processed so far ( for chunked imports )")
        int chunksProcessed,

        @Schema(description = "Progress percentage 0 to 100 ; null when total is unknown")
        Double progressPercentage,

        @Schema(description = "Cumulative bytes written to disk / streamed to client")
        long bytesTotal,

        @Schema(description = "Total records expected ( header excluded ) ; 0 if unknown. "
                + "Permet à l'UI de basculer la progress bar en mode déterminé.")
        long recordsTotal,

        @Schema(description = "Etat live des chunks composant le workflow ( drill-down "
                + "oa-live ). Vide quand le workflow ne s'expose pas par chunks ou tant "
                + "qu'aucun chunk n'a demarre.")
        List<ChunkDTO> chunks) {

    public static DashboardWorkflowDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot s) {
        List<ChunkDTO> chunkDtos = s.chunks() == null
                ? List.of()
                : s.chunks().stream().map(ChunkDTO::fromSnapshot).toList();
        return new DashboardWorkflowDTO(
                s.correlationId(), s.workflowType(), s.userId(), s.userLogin(),
                s.applicationName(), s.dataType(), s.resourceName(),
                s.startTime(), null, null,
                s.status(),
                s.recordsProcessed(), s.recordsFailed(), s.chunksProcessed(),
                s.progressPercentage(), s.bytesTotal(), s.recordsTotal(),
                chunkDtos);
    }

    /** Per-chunk DTO mirroring {@link fr.inra.oresing.workflow.cascade.history.ChunkSnapshot}. */
    @Schema(name = "DashboardChunk",
            description = "Per-chunk live state for the oa-live drill-down view")
    public record ChunkDTO(
            @Schema(description = "Chunk index ( 0 , 1 , 2 , ... )")
            int chunkIndex,
            @Schema(description = "Chunk status",
                    allowableValues = {"RUNNING", "COMPLETED", "FAILED", "CANCELLED"})
            String status,
            @Schema(description = "Lignes deja traitees pour ce chunk")
            long recordsProcessed,
            @Schema(description = "Total de lignes attendues pour ce chunk ; 0 si inconnu")
            long recordsTotal,
            @Schema(description = "Pourcentage 0..100 ; null si recordsTotal inconnu")
            Double progressPercentage,
            @Schema(description = "Thread / virtual thread qui traite le chunk")
            String workerName,
            @Schema(description = "Demarrage du chunk ( ISO-8601 )")
            Instant startTime,
            @Schema(description = "Fin du chunk - null tant que le chunk tourne")
            Instant endTime,
            @Schema(description = "Message d'erreur si status = FAILED")
            String errorMessage) {

        public static ChunkDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.ChunkSnapshot c) {
            return new ChunkDTO(
                    c.chunkIndex(), c.status(),
                    c.recordsProcessed(), c.recordsTotal(),
                    c.progressPercentage(),
                    c.workerName(), c.startTime(), c.endTime(),
                    c.errorMessage());
        }
    }

    /**
     * Detail DTO adds errors array , fatal error message , metadata map on
     * top of the summary - returned by /api/dashboard/workflows/{correlationId}.
     */
    @Schema(name = "DashboardWorkflowDetail",
            description = "Extended DashboardWorkflow with errors and metadata for the detail view")
    public record Detail(
            DashboardWorkflowDTO summary,
            String fatalError,
            List<String> errors,
            Map<String, Object> metadata) {
    }

    /**
     * Paginated wrapper for history.
     */
    @Schema(name = "DashboardWorkflowList",
            description = "Paginated workflow history result")
    public record Page(
            List<DashboardWorkflowDTO> items,
            long total,
            int limit,
            int offset) {
    }
}
