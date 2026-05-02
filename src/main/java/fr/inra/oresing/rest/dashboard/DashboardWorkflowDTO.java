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
 * and the columns of oa_audit.workflow_log.
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
                allowableValues = {"IN_PROGRESS", "UPLOADING", "CHUNKING", "PROCESSING", "LOADING_DB",
                        "COMPLETED", "FAILED", "CANCELLED", "RATE_LIMITED"})
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
        List<ChunkDTO> chunks,

        @Schema(description = "Vue agrégée par worker ( 1 ligne par thread ). Stable "
                + "même quand le nombre de chunks explose ; remplace l'usage du tableau "
                + "chunks pour la vue Workers de oa-live.")
        List<WorkerDTO> workers,

        @Schema(description = "Parallélisme effectif par stage ( source / transform / "
                + "sink ) tel que résolu par le builder cascade. Null pour les workflows "
                + "non chunkés.")
        ParallelismDTO parallelism,

        @Schema(description = "Cascade strategy effectivement utilisée pour ce workflow "
                + "( sinkStrategy , stagingStrategy , executionMode , streamingMode , "
                + "directWriteParallel ). Null pour les workflows non chunkés.")
        StrategyDTO strategy,

        @Schema(description = "Liste glissante des derniers chunks ecrits par le sink "
                + "( cap 1000 ) . Alimente la modal SINK ( drill-down 'fichiers charges "
                + "en base' ) . Vide pour les workflows non chunkes ou tant qu'aucun "
                + "chunk n'a ete ecrit.")
        List<SinkChunkDTO> sinkChunks) {

    public static DashboardWorkflowDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot s) {
        List<ChunkDTO> chunkDtos = s.chunks() == null
                ? List.of()
                : s.chunks().stream().map(ChunkDTO::fromSnapshot).toList();
        List<WorkerDTO> workerDtos = s.workers() == null
                ? List.of()
                : s.workers().stream().map(WorkerDTO::fromSnapshot).toList();
        ParallelismDTO parallelism = s.parallelism() == null
                ? null
                : ParallelismDTO.fromSnapshot(s.parallelism());
        StrategyDTO strategy = s.strategy() == null
                ? null
                : StrategyDTO.fromSnapshot(s.strategy());
        List<SinkChunkDTO> sinkChunkDtos = s.sinkChunks() == null
                ? List.of()
                : s.sinkChunks().stream().map(SinkChunkDTO::fromRecord).toList();
        return new DashboardWorkflowDTO(
                s.correlationId(), s.workflowType(), s.userId(), s.userLogin(),
                s.applicationName(), s.dataType(), s.resourceName(),
                s.startTime(), null, null,
                s.status(),
                s.recordsProcessed(), s.recordsFailed(), s.chunksProcessed(),
                s.progressPercentage(), s.bytesTotal(), s.recordsTotal(),
                chunkDtos, workerDtos, parallelism, strategy, sinkChunkDtos);
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

    /** Per-worker DTO mirroring {@link fr.inra.oresing.workflow.cascade.history.WorkerSnapshot}. */
    @Schema(name = "DashboardWorker",
            description = "Per-worker live state for the oa-live Workers view")
    public record WorkerDTO(
            @Schema(description = "SOURCE | TRANSFORM | SINK")
            String stage,
            @Schema(description = "Worker thread name ( e.g. transform-1 )")
            String name,
            @Schema(description = "RUNNING | IDLE | FAILED")
            String status,
            @Schema(description = "Index of the chunk currently being processed ; null when IDLE")
            Integer currentChunk,
            @Schema(description = "Records already processed within the current chunk ( 0 when IDLE )")
            long currentChunkRecordsProcessed,
            @Schema(description = "Total records expected for the current chunk ( 0 when IDLE or unknown )")
            long currentChunkRecordsTotal,
            @Schema(description = "Progress percentage 0..100 of the current chunk ; null when unknown / IDLE")
            Double currentChunkProgressPercentage,
            @Schema(description = "Counter of chunks already handled by this worker")
            int chunkCount,
            @Schema(description = "Wall-clock duration of the most recently finished chunk in milliseconds ; null if none yet")
            Long lastChunkDurationMs,
            @Schema(description = "Rolling average duration over the last finished chunks ( up to 10 ) in milliseconds ; null if none yet")
            Long avgChunkDurationMs,
            @Schema(description = "Last time this worker emitted any event ( ISO-8601 )")
            Instant lastActivity) {

        public static WorkerDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.WorkerSnapshot w) {
            Long lastMs = w.lastChunkDuration() == null ? null : w.lastChunkDuration().toMillis();
            Long avgMs  = w.avgChunkDuration()  == null ? null : w.avgChunkDuration().toMillis();
            return new WorkerDTO(
                    w.stage(), w.name(), w.status(), w.currentChunk(),
                    w.currentChunkRecordsProcessed(),
                    w.currentChunkRecordsTotal(),
                    w.currentChunkProgressPercentage(),
                    w.chunkCount(), lastMs, avgMs, w.lastActivity());
        }
    }

    /** Parallelism summary mirroring {@link fr.inra.oresing.workflow.cascade.history.ParallelismSnapshot}. */
    @Schema(name = "DashboardParallelism",
            description = "Per-stage parallelism summary shown in the oa-live Workers view header")
    public record ParallelismDTO(
            int source,
            int transform,
            int sink) {
        public static ParallelismDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.ParallelismSnapshot p) {
            return new ParallelismDTO(p.source(), p.transform(), p.sink());
        }
    }

    /** Per-chunk sink-write record mirroring {@link fr.inra.oresing.workflow.cascade.history.SinkChunkRecord}. */
    @Schema(name = "DashboardSinkChunk",
            description = "Per-chunk sink-write record for the oa-live SINK drill-down modal")
    public record SinkChunkDTO(
            int     chunkIndex,
            String  workerName,
            String  status,
            long    durationMs,
            java.time.Instant at,
            String  errorMessage) {
        public static SinkChunkDTO fromRecord(fr.inra.oresing.workflow.cascade.history.SinkChunkRecord r) {
            return new SinkChunkDTO(r.chunkIndex(), r.workerName(), r.status(),
                    r.durationMs(), r.at(), r.errorMessage());
        }
    }

    /** Cascade strategy summary mirroring {@link fr.inra.oresing.workflow.cascade.history.StrategySnapshot}. */
    @Schema(name = "DashboardStrategy",
            description = "Cascade strategy summary shown in the oa-live Workers view header")
    public record StrategyDTO(
            @Schema(description = "Sink strategy : MERGE_FILE | DIRECT_COPY")
            String sinkStrategy,
            @Schema(description = "Staging strategy : PER_CONNECTION_TEMP | SHARED_UNLOGGED | PER_WORKFLOW_TABLE ; null when sinkStrategy = MERGE_FILE")
            String stagingStrategy,
            @Schema(description = "Cascade pipeline mode : STAGED | PIPELINED ( cascade 2.1.0 )")
            String pipelineMode,
            @Schema(description = "Number of concurrent sink workers")
            int    sinkParallelism) {
        public static StrategyDTO fromSnapshot(fr.inra.oresing.workflow.cascade.history.StrategySnapshot s) {
            return new StrategyDTO(s.sinkStrategy(), s.stagingStrategy(),
                    s.pipelineMode(), s.sinkParallelism());
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
